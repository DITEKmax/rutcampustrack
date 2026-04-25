package ru.rutcampustrack.notification.csp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.shared.observability.MetricNames;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * M13 G16 — endpoint для CSP violation reports от браузеров.
 *
 * <p>Браузеры POST'ят сюда согласно директиве {@code report-uri}
 * (старый формат, Firefox + Safari) или {@code report-to} с Report-To
 * header (новый Reporting API, Chrome 97+).
 *
 * <p>Поддерживаемые форматы тела (выбор по Content-Type header):
 * <ul>
 *   <li>{@code application/csp-report} — single violation (legacy):
 *   <pre>{"csp-report": {"violated-directive": "...", "blocked-uri": "..."}}</pre>
 *   </li>
 *   <li>{@code application/reports+json} — batch (Reporting API):
 *   <pre>[{"type": "csp-violation", "body": {"effectiveDirective": "...", "blockedURL": "..."}}, ...]</pre>
 *   </li>
 *   <li>{@code application/json} — fallback (некоторые прокси переписывают Content-Type).</li>
 * </ul>
 *
 * <p>Body читаем как raw {@code byte[]} и парсим Jackson'ом вручную —
 * Spring's {@code MappingJackson2HttpMessageConverter} не регистрирует
 * {@code application/csp-report} (нестандартный MIME), поэтому роутинг
 * через {@code consumes=...} в обычном @RequestBody Map приводил к 415.
 *
 * <p>Каждый violation → structured log (WARN) + counter
 * {@link MetricNames#CSP_VIOLATIONS} с тегами:
 * <ul>
 *   <li>{@code directive} — violated / effective directive (e.g. "script-src").</li>
 *   <li>{@code blocked_uri_host} — только host (без path) blocked URI.
 *   Path отбрасывается чтобы избежать high-cardinality labels в Prometheus.</li>
 * </ul>
 *
 * <p>Endpoint публично доступен (браузеры не носят Internal JWT).
 * Защита от flood — rate-limit в gateway (см. {@code api-gateway/application.yml}).
 *
 * <p>Response — всегда 204 No Content. Браузер не ждёт ответа, но 2xx
 * важно чтобы не заспамить DevTools «CSP report failed to deliver».
 */
@RestController
@RequestMapping("/csp-report")
@Hidden  // Infrastructure endpoint (browser callback) — не часть публичного OpenAPI.
public class CspReportController {

    private static final Logger log = LoggerFactory.getLogger(CspReportController.class);

    private static final String UNKNOWN_DIRECTIVE = "unknown";
    private static final String UNKNOWN_HOST = "unknown";

    private static final String MT_CSP_REPORT = "application/csp-report";
    private static final String MT_REPORTS_API = "application/reports+json";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_TYPE = new TypeReference<>() {};

    private final MeterRegistry registry;
    private final ObjectMapper mapper;

    public CspReportController(MeterRegistry registry, ObjectMapper mapper) {
        this.registry = registry;
        this.mapper = mapper;
    }

    /**
     * Принимает любой Content-Type — body читается как byte[], парсится в
     * зависимости от Content-Type. Не используем {@code consumes=...} т.к.
     * Jackson converter не знает про {@code application/csp-report}.
     */
    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "Content-Type", required = false) String contentType,
                                        @RequestBody(required = false) byte[] body) {
        if (body == null || body.length == 0) {
            return ResponseEntity.noContent().build();
        }

        String ct = contentType == null ? "" : contentType.toLowerCase();

        if (ct.startsWith(MT_REPORTS_API)) {
            parseReportsApi(body);
        } else if (ct.startsWith(MT_CSP_REPORT)
                || ct.startsWith("application/json")
                || ct.startsWith("application/problem+json")
                || ct.contains("+json")) {
            parseLegacy(body);
        } else {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    private void parseLegacy(byte[] body) {
        Map<String, Object> json = readJson(body, MAP_TYPE);
        if (json == null) {
            return;
        }

        Object report = json.get("csp-report");
        if (report instanceof Map<?, ?> r) {
            recordViolation(
                    stringOrNull(r.get("violated-directive")),
                    stringOrNull(r.get("blocked-uri")),
                    stringOrNull(r.get("document-uri")));
            return;
        }

        // Некоторые прокси переписывают Content-Type на application/json,
        // а поля остаются в kebab-case. Пробуем top-level keys.
        Object vd = json.get("violated-directive");
        if (vd != null) {
            recordViolation(
                    stringOrNull(vd),
                    stringOrNull(json.get("blocked-uri")),
                    stringOrNull(json.get("document-uri")));
        } else {
            log.debug("CSP legacy payload без 'csp-report' wrapper и без top-level fields — dropped");
        }
    }

    private void parseReportsApi(byte[] body) {
        List<Map<String, Object>> reports = readJson(body, LIST_TYPE);
        if (reports == null || reports.isEmpty()) {
            return;
        }
        for (Map<String, Object> entry : reports) {
            if (!"csp-violation".equals(entry.get("type"))) {
                continue; // network-error / intervention / deprecation — не наш домен
            }
            if (entry.get("body") instanceof Map<?, ?> rb) {
                // Reporting API: camelCase effectiveDirective/blockedURL/documentURL.
                recordViolation(
                        stringOrNull(rb.get("effectiveDirective")),
                        stringOrNull(rb.get("blockedURL")),
                        stringOrNull(rb.get("documentURL")));
            }
        }
    }

    private <T> T readJson(byte[] body, TypeReference<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            log.debug("CSP report malformed JSON: {}", e.getOriginalMessage());
            return null;
        } catch (Exception e) {
            log.debug("CSP report parse failure: {}", e.getMessage());
            return null;
        }
    }

    private void recordViolation(String directive, String blockedUri, String documentUri) {
        String d = normalizeDirective(directive);
        String host = extractHost(blockedUri);
        Counter.builder(MetricNames.CSP_VIOLATIONS)
                .description("CSP violation reports от браузеров")
                .tag("directive", d)
                .tag("blocked_uri_host", host)
                .register(registry)
                .increment();
        // Structured log на WARN — видно в Grafana/Loki (masking filter
        // из shared-logback не трогает CSP-поля).
        log.warn("CSP violation: directive={} blockedUri={} documentUri={}",
                d, safe(blockedUri), safe(documentUri));
    }

    private static String normalizeDirective(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN_DIRECTIVE;
        }
        // «script-src 'self' https://example.com» → «script-src». Оставляем
        // только имя директивы (первый токен), источники отбрасываем
        // (высокая кардинальность).
        int space = raw.indexOf(' ');
        String name = space > 0 ? raw.substring(0, space) : raw;
        return name.toLowerCase();
    }

    private static String extractHost(String uri) {
        if (uri == null || uri.isBlank()) {
            return UNKNOWN_HOST;
        }
        // Специальные CSP-значения не ходят через URI parser: "inline",
        // "eval", "data", "blob" — вернём as-is (low-cardinality).
        if (!uri.contains("://") && !uri.startsWith("//")) {
            String lower = uri.toLowerCase();
            return lower.length() > 32 ? lower.substring(0, 32) : lower;
        }
        try {
            URI parsed = URI.create(uri);
            String h = parsed.getHost();
            return h == null || h.isBlank() ? UNKNOWN_HOST : h.toLowerCase();
        } catch (IllegalArgumentException e) {
            return UNKNOWN_HOST;
        }
    }

    private static String stringOrNull(Object v) {
        return v == null ? null : v.toString();
    }

    private static String safe(String s) {
        if (s == null) return "";
        // Обрезаем до 512 чтобы не раздуть лог-line (CSP blockedUri может
        // быть длинным data: URI).
        return s.length() > 512 ? s.substring(0, 512) + "..." : s;
    }
}
