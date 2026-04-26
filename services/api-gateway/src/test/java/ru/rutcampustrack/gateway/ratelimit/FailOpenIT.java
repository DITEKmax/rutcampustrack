package ru.rutcampustrack.gateway.ratelimit;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * M03a Группа 12: fail-open проверка (NEW-9). Redis указан на заведомо недоступный
 * порт (127.0.0.1:1 = connection refused) — 10 запросов должны пройти (allowed=true),
 * НЕ застрять и не дать 429.
 */
// M14 G9: @ActiveProfiles("test") нужен чтобы RequiredSecretsValidator
// (G4 v2) skip'ал — у этого IT нет .env / DynamicPropertySource для
// REDIS_PASSWORD/INTERNAL_ISSUER_SECRET (test использует Redis с
// connection refused для fail-open verification).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FailOpenIT {

    static WireMockServer WIREMOCK;

    @Autowired
    WebTestClient client;

    @BeforeAll
    static void startInfra() {
        WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());
        WIREMOCK.start();
        WIREMOCK.stubFor(get(urlEqualTo("/auth/public-key")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"publicKey\":\"-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7PZ7h1J+Uth3VnhqF7eG7H/r4v0h3OdW+k3H8Cc5zWWpJs3oQFFxKtMSY6T4Uf7Qm5JtX7sX5+cJF9o+1nD4G3xWwPt2k0gzY5o7a3pFFkYl5gKh4PqQOd3Gg7E8iH+yP5gPj9oMtSj8Mx3f+mKXR8/FiZP7FLFQJ7t2LhJGn7bYhDQLVJLfSg8F7Y4XQ8QqCXVZSxmCJz7xpQO3/ez+LN6I0JaN+P6DYxNBqjZ5v4jV5zV3cN9E9XKbG8p4jGJ+QIDAQAB\\n-----END PUBLIC KEY-----\",\"algorithm\":\"RS256\"}")));
        WIREMOCK.stubFor(post(urlPathMatching("/auth/otp/verify-by-code"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
        WIREMOCK.stubFor(any(urlPathMatching("/auth/.*")).atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }

    @AfterAll
    static void stopInfra() {
        if (WIREMOCK != null) WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        // Redis = connection refused
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", () -> 1);
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.data.redis.timeout", () -> "500ms");
        r.add("spring.data.redis.connect-timeout", () -> "500ms");
        // Lettuce по-умолчанию делает eager connect на старте — выключаем shutdown timeout
        r.add("spring.data.redis.lettuce.shutdown-timeout", () -> "100ms");
        r.add("gateway.auth-service-url", () -> "http://localhost:" + WIREMOCK.port());
        r.add("rutcampustrack.security.internal-issuer-client.auth-service-url",
                () -> "http://localhost:" + WIREMOCK.port());
        r.add("AUTH_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ACADEMIC_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("SCHEDULE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ATTENDANCE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("NOTIFICATION_WEB_URL", () -> "http://localhost:" + WIREMOCK.port());
    }

    @Test
    @DisplayName("Redis down → все 10 запросов проходят (fail-open, НЕ 429, НЕ timeout)")
    void redisDown_allRequestsPass() {
        for (int i = 0; i < 10; i++) {
            client.post().uri("/api/auth/otp/verify-by-code")
                    .header("X-Forwarded-For", "10.9.9.9")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"code\":\"1234\"}")
                    .exchange()
                    .expectStatus().is2xxSuccessful();
            // X-RateLimit-FailOpen — выставляется в Response.getHeaders(), может быть
            // затёрт downstream'ом до клиента. Unit FailOpenRateLimiterTest проверяет
            // наличие header'а на уровне RateLimiter.Response.
        }
    }
}
