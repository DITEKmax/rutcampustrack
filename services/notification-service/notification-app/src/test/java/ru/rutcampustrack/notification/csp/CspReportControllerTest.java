package ru.rutcampustrack.notification.csp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.shared.observability.MetricNames;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G16 — unit tests для {@link CspReportController}.
 *
 * <p>Покрывает:
 * <ul>
 *   <li>Legacy csp-report format (single violation wrapped в {@code csp-report}).</li>
 *   <li>Legacy format с top-level fields (прокси-rewritten Content-Type).</li>
 *   <li>Modern reports+json format (batch array).</li>
 *   <li>Low-cardinality labels: directive normalized, blocked_uri_host extracted.</li>
 *   <li>Edge cases: null/empty body, non-csp-violation type, unsupported Content-Type.</li>
 *   <li>Specials: "inline"/"eval" blocked-uri → as-is label.</li>
 * </ul>
 */
class CspReportControllerTest {

    private MeterRegistry registry;
    private CspReportController controller;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        controller = new CspReportController(registry, new ObjectMapper());
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void legacyFormat_singleViolation_incrementsCounter() {
        String json = """
                {"csp-report": {
                  "violated-directive": "script-src 'self'",
                  "blocked-uri": "https://evil.example.com/malware.js",
                  "document-uri": "https://ruttrack.site/admin/users"
                }}
                """;

        var response = controller.receive("application/csp-report", bytes(json));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "script-src")
                .tag("blocked_uri_host", "evil.example.com")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void legacyFormat_topLevelFields_incrementsCounter() {
        String json = """
                {
                  "violated-directive": "img-src",
                  "blocked-uri": "http://tracker.example.com/pixel.gif"
                }
                """;

        var response = controller.receive("application/json", bytes(json));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "img-src")
                .tag("blocked_uri_host", "tracker.example.com")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void reportsApiFormat_batchArray_incrementsPerEntry() {
        String json = """
                [
                  {"type": "csp-violation", "body": {
                    "effectiveDirective": "style-src",
                    "blockedURL": "https://cdn.example.com/theme.css",
                    "documentURL": "https://ruttrack.site/dashboard"
                  }},
                  {"type": "csp-violation", "body": {
                    "effectiveDirective": "connect-src",
                    "blockedURL": "wss://spy.example.com/ws",
                    "documentURL": "https://ruttrack.site/app/"
                  }}
                ]
                """;

        var response = controller.receive("application/reports+json", bytes(json));

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "style-src")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "connect-src")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void reportsApiFormat_nonCspViolationType_ignored() {
        String json = """
                [
                  {"type": "network-error", "body": {"url": "https://..."}},
                  {"type": "intervention", "body": {"id": "x"}}
                ]
                """;

        controller.receive("application/reports+json", bytes(json));

        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void directiveNormalization_stripsSourceList() {
        String json = """
                {"csp-report": {
                  "violated-directive": "script-src-elem 'self' 'unsafe-inline' https://cdn.example.com",
                  "blocked-uri": "https://ads.example.com/sdk.js"
                }}
                """;

        controller.receive("application/csp-report", bytes(json));

        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "script-src-elem")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void blockedUri_specialValue_inline_keptAsIs() {
        String json = """
                {"csp-report": {
                  "violated-directive": "script-src",
                  "blocked-uri": "inline"
                }}
                """;

        controller.receive("application/csp-report", bytes(json));

        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("blocked_uri_host", "inline")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void nullBody_gracefullyHandled() {
        var response = controller.receive("application/csp-report", null);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void emptyBody_gracefullyHandled() {
        var response = controller.receive("application/csp-report", new byte[0]);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void emptyReportsArray_noCounter() {
        var response = controller.receive("application/reports+json", bytes("[]"));
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void missingDirective_defaultsToUnknown() {
        String json = """
                {"csp-report": {"blocked-uri": "https://example.com/x"}}
                """;

        controller.receive("application/csp-report", bytes(json));

        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "unknown")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void missingBlockedUri_hostDefaultsToUnknown() {
        String json = """
                {"csp-report": {"violated-directive": "font-src"}}
                """;

        controller.receive("application/csp-report", bytes(json));

        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("blocked_uri_host", "unknown")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void unsupportedContentType_returns415() {
        var response = controller.receive("text/plain", bytes("anything"));
        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void missingContentType_returns415() {
        var response = controller.receive(null, bytes("{}"));
        assertThat(response.getStatusCode().value()).isEqualTo(415);
    }

    @Test
    void malformedJson_gracefullyDropsWithoutException() {
        var response = controller.receive("application/csp-report", bytes("{ not json"));
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS).counter()).isNull();
    }

    @Test
    void directiveCaseNormalizedToLowercase() {
        String json = """
                {"csp-report": {
                  "violated-directive": "SCRIPT-SRC 'self'",
                  "blocked-uri": "https://EVIL.example.com/x"
                }}
                """;

        controller.receive("application/csp-report", bytes(json));

        // Host lowercased + directive lowercased (Prometheus labels
        // не должны иметь case-sensitive дубли).
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "script-src")
                .tag("blocked_uri_host", "evil.example.com")
                .counter().count()).isEqualTo(1.0);
    }
}
