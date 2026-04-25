package ru.rutcampustrack.notification.csp;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.notification.push.PushSubscriptionRepository;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.shared.observability.MetricNames;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G16 — IT для /csp-report endpoint (через MockMvc).
 *
 * <p>Проверяет что:
 * <ul>
 *   <li>Endpoint достижим БЕЗ Internal JWT headers (auth excluded).</li>
 *   <li>Legacy {@code application/csp-report} → 204 + counter increment.</li>
 *   <li>Modern {@code application/reports+json} → 204 + counter increment.</li>
 *   <li>Unsupported Content-Type → 415.</li>
 *   <li>Counter реально зарегистрирован в глобальном {@link MeterRegistry}
 *   (значит /actuator/prometheus expose'ит его).</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv"
})
@AutoConfigureMockMvc
class CspReportIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    CspReportIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MeterRegistry registry;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private PushSubscriptionRepository pushRepo;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private WebPushDeliveryService delivery;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    private double counterBefore;

    @BeforeEach
    void snapshotCounter() {
        // SpringBoot context reused между тестами — счётчик тоже persist'ится.
        // Фиксируем baseline и сравниваем delta, а не absolute.
        counterBefore = totalCspCounter();
    }

    @Test
    @DisplayName("Legacy application/csp-report → 204 + counter increments")
    void legacyFormat() throws Exception {
        String json = """
                {
                  "csp-report": {
                    "violated-directive": "script-src 'self'",
                    "blocked-uri": "https://evil.example.com/sdk.js",
                    "document-uri": "https://ruttrack.site/admin"
                  }
                }
                """;

        mvc.perform(post("/csp-report")
                        .contentType("application/csp-report")
                        .content(json))
                .andExpect(status().isNoContent());

        assertThat(totalCspCounter() - counterBefore).isEqualTo(1.0);
        assertThat(registry.find(MetricNames.CSP_VIOLATIONS)
                .tag("directive", "script-src")
                .tag("blocked_uri_host", "evil.example.com")
                .counter())
                .as("counter с нужными тегами зарегистрирован")
                .isNotNull();
    }

    @Test
    @DisplayName("Modern application/reports+json → 204 + counter increments per entry")
    void modernReportsApi() throws Exception {
        String json = """
                [
                  {
                    "type": "csp-violation",
                    "body": {
                      "effectiveDirective": "img-src",
                      "blockedURL": "https://cdn.example.com/pixel.gif",
                      "documentURL": "https://ruttrack.site/dashboard"
                    }
                  },
                  {
                    "type": "csp-violation",
                    "body": {
                      "effectiveDirective": "connect-src",
                      "blockedURL": "wss://spy.example.com/ws",
                      "documentURL": "https://ruttrack.site/app/"
                    }
                  }
                ]
                """;

        mvc.perform(post("/csp-report")
                        .contentType("application/reports+json")
                        .content(json))
                .andExpect(status().isNoContent());

        assertThat(totalCspCounter() - counterBefore).isEqualTo(2.0);
    }

    @Test
    @DisplayName("application/json (прокси-rewritten) с top-level fields → 204 + counter increments")
    void plainJsonWithTopLevelFields() throws Exception {
        String json = """
                {
                  "violated-directive": "font-src",
                  "blocked-uri": "https://fonts.example.com/font.woff2"
                }
                """;

        mvc.perform(post("/csp-report")
                        .contentType("application/json")
                        .content(json))
                .andExpect(status().isNoContent());

        assertThat(totalCspCounter() - counterBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Unsupported Content-Type → 415")
    void unsupportedContentType() throws Exception {
        mvc.perform(post("/csp-report")
                        .contentType("text/plain")
                        .content("something"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(totalCspCounter() - counterBefore).isZero();
    }

    @Test
    @DisplayName("Endpoint НЕ требует Internal JWT / X-User-* headers")
    void noAuthRequired() throws Exception {
        // Без заголовков (filter в DualModeUserContextFilter бы упал бы
        // с 401 на не-excluded path'е — проверяем что exclude работает).
        String json = """
                {"csp-report": {"violated-directive": "default-src", "blocked-uri": "https://x.example.com"}}
                """;

        mvc.perform(post("/csp-report")
                        .contentType("application/csp-report")
                        .content(json))
                .andExpect(status().isNoContent());
    }

    private double totalCspCounter() {
        return registry.find(MetricNames.CSP_VIOLATIONS).counters().stream()
                .mapToDouble(io.micrometer.core.instrument.Counter::count)
                .sum();
    }
}
