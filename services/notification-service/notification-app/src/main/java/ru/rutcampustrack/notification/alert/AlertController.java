package ru.rutcampustrack.notification.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * M04 Группа 9 — webhook endpoint для Alertmanager (D2=(a)).
 *
 * <p>Alertmanager шлёт POST /internal/alert с телом в формате v4:
 * <pre>
 * {
 *   "alerts": [
 *     {
 *       "status": "firing",
 *       "labels": { "alertname": "ServiceDown", "severity": "critical", ... },
 *       "annotations": { "summary": "...", "description": "..." }
 *     },
 *     ...
 *   ],
 *   "groupLabels": {...},
 *   "version": "4"
 * }
 * </pre>
 *
 * <p>Каждый alert превращается в отдельное событие {@code alert.fired}
 * в RabbitMQ, которое ловит notification-bot и форвардит в Telegram.
 *
 * <p>Аутентификация: header {@code Authorization: Bearer <secret>}
 * (shared secret из .env). В M06 заменится на mTLS.
 *
 * <p>Endpoint исключён из security filter (см.
 * {@code NotificationUserContextFilter.isExcludedPath}) — Alertmanager
 * не умеет носить Internal JWT.
 */
@RestController
@RequestMapping("/internal/alert")
@EnableConfigurationProperties(AlertProperties.class)
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertPublisher publisher;
    private final AlertProperties properties;

    public AlertController(AlertPublisher publisher, AlertProperties properties) {
        this.publisher = publisher;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<?> receive(@RequestHeader(value = "Authorization", required = false) String auth,
                                     @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) {
            log.warn("Unauthorized alert webhook attempt — missing/invalid Bearer token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Object alertsObj = body.get("alerts");
        if (!(alertsObj instanceof List<?> alerts)) {
            log.warn("Invalid webhook payload — no 'alerts' array");
            return ResponseEntity.badRequest().body(Map.of("error", "alerts array missing"));
        }

        int published = 0;
        for (Object raw : alerts) {
            if (!(raw instanceof Map<?, ?> alert)) continue;
            publisher.publishFired(toPayload(alert));
            published++;
        }
        log.info("Alert webhook processed: {} alerts published", published);
        return ResponseEntity.ok(Map.of("published", published));
    }

    private boolean isAuthorized(String auth) {
        String secret = properties.webhookSecret();
        if (secret == null || secret.isBlank()) {
            // Если secret не сконфигурирован — endpoint полностью закрыт
            // (fail-safe default). Альтернатива «открыт без secret» опасна.
            return false;
        }
        if (auth == null || !auth.startsWith("Bearer ")) {
            return false;
        }
        String provided = auth.substring("Bearer ".length()).trim();
        // G11 LOW fix: constant-time сравнение, защита от timing attack
        // (low risk в private_net, но тривиально исправляется).
        return java.security.MessageDigest.isEqual(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private AlertPayload toPayload(Map<?, ?> alert) {
        // G11 H3 fix: coerce к Map<String,String> безопасно. Unchecked
        // cast тихо пропускал non-string значения и падал позже в
        // подписчике (bot) при HTML-escape / JSON десериализации.
        Map<String, String> labels = coerceStringMap(alert.get("labels"));
        Map<String, String> annotations = coerceStringMap(alert.get("annotations"));
        return new AlertPayload(
                labels.get("alertname"),
                labels.get("severity"),
                String.valueOf(alert.get("status")),
                annotations.get("summary"),
                annotations.get("description"),
                labels
        );
    }

    private static Map<String, String> coerceStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> src)) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>(src.size());
        for (Map.Entry<?, ?> e : src.entrySet()) {
            if (e.getKey() == null) continue;
            String k = e.getKey().toString();
            String v = e.getValue() == null ? "" : e.getValue().toString();
            out.put(k, v);
        }
        return out;
    }
}
