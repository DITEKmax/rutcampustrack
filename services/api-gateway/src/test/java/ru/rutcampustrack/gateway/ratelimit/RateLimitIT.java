package ru.rutcampustrack.gateway.ratelimit;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M03a Группа 12: rate-limit smoke-test на реальном Redis + WireMock-auth-service.
 *
 * <p>Проверяет контракт 14 P1-2: 5-й запрос на OTP verify-by-code от одного IP
 * проходит (limit=5/min), 6-й получает 429 с RFC 7807 Problem Details
 * + {@code Retry-After}.</p>
 *
 * <p>M13 G2 — семантика перекалибрована на настоящий 5 req/min через
 * token-bucket: {@code replenishRate=1, burstCapacity=60, requestedTokens=12}.
 * Bucket вмещает 60 токенов, 5 запросов × 12 = 60 тратят всё залпом, 6-й
 * получает 429. Далее восполнение 1 токен/сек = следующий слот через
 * 12 сек. {@code Retry-After: 60} остаётся hard-coded upper bound в
 * {@link ru.rutcampustrack.gateway.ratelimit.RateLimitProblemDetailsFilter}
 * и не зависит от route-specific requestedTokens.</p>
 */
// M14 G9: @ActiveProfiles("test") нужен для RequiredSecretsValidator
// (G4 v2) skip — IT использует Testcontainers Redis + WireMock auth,
// REDIS_PASSWORD/INTERNAL_ISSUER_SECRET не выставлены в env.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RateLimitIT {

    // M08 D5 — reuse=true; локально через ~/.testcontainers.properties.
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(true);

    static WireMockServer WIREMOCK;

    @Autowired
    WebTestClient client;

    @Autowired
    ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void startInfra() {
        REDIS.start();
        WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());
        WIREMOCK.start();
        // stub auth-service public-key (needed by PublicKeyConfig @PostConstruct)
        WIREMOCK.stubFor(get(urlEqualTo("/auth/public-key")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"publicKey\":\"-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7PZ7h1J+Uth3VnhqF7eG7H/r4v0h3OdW+k3H8Cc5zWWpJs3oQFFxKtMSY6T4Uf7Qm5JtX7sX5+cJF9o+1nD4G3xWwPt2k0gzY5o7a3pFFkYl5gKh4PqQOd3Gg7E8iH+yP5gPj9oMtSj8Mx3f+mKXR8/FiZP7FLFQJ7t2LhJGn7bYhDQLVJLfSg8F7Y4XQ8QqCXVZSxmCJz7xpQO3/ez+LN6I0JaN+P6DYxNBqjZ5v4jV5zV3cN9E9XKbG8p4jGJ+QIDAQAB\\n-----END PUBLIC KEY-----\",\"algorithm\":\"RS256\"}")));
        // stub otp verify — любой POST возвращает 200
        WIREMOCK.stubFor(post(urlPathMatching("/auth/otp/verify-by-code"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
        // stub любые другие /auth/** чтобы downstream не ломал тест
        WIREMOCK.stubFor(any(urlPathMatching("/auth/.*"))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }

    @AfterAll
    static void stopInfra() {
        if (WIREMOCK != null) WIREMOCK.stop();
        REDIS.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");  // no auth in testcontainer
        r.add("gateway.auth-service-url", () -> "http://localhost:" + WIREMOCK.port());
        r.add("rutcampustrack.security.internal-issuer-client.auth-service-url",
                () -> "http://localhost:" + WIREMOCK.port());
        // Route URIs переопределяем на WireMock чтобы downstream DNS не запрашивался
        r.add("AUTH_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ACADEMIC_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("SCHEDULE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ATTENDANCE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("NOTIFICATION_WEB_URL", () -> "http://localhost:" + WIREMOCK.port());
    }

    @Test
    @DisplayName("/api/auth/otp/verify-by-code: 5 req/min per IP — 6-й 429 c Problem Details + Retry-After")
    void sixthRequest_returns429() {
        // Очистка Redis-ключей rate-limit перед тестом
        redis.keys("request_rate_limiter*").flatMap(redis::delete).blockLast();

        String ip = "198.51.100.42";

        // 5 разрешённых запросов
        for (int i = 0; i < 5; i++) {
            client.post().uri("/api/auth/otp/verify-by-code")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"code\":\"1234\",\"login\":\"x\"}")
                    .exchange()
                    .expectStatus().is2xxSuccessful();
        }

        // 6-й → 429
        client.post().uri("/api/auth/otp/verify-by-code")
                .header("X-Forwarded-For", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"1234\",\"login\":\"x\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
                .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("\"status\":429")
                            .contains("\"type\":\"https://ruttrack.site/problems/rate-limit-exceeded\"")
                            .contains("\"title\":\"Too Many Requests\"");
                });
    }

    @Test
    @DisplayName("Разные IP имеют раздельные корзины (5+5 с двух IP = обе разрешены)")
    void differentIps_separateBuckets() {
        redis.keys("request_rate_limiter*").flatMap(redis::delete).blockLast();

        for (int i = 0; i < 5; i++) {
            client.post().uri("/api/auth/otp/verify-by-code")
                    .header("X-Forwarded-For", "10.1.1.1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"code\":\"1234\"}")
                    .exchange()
                    .expectStatus().is2xxSuccessful();
        }
        // 6-й с IP-A должен быть 429
        client.post().uri("/api/auth/otp/verify-by-code")
                .header("X-Forwarded-For", "10.1.1.1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"1234\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // А первый запрос с IP-B — проходит
        client.post().uri("/api/auth/otp/verify-by-code")
                .header("X-Forwarded-For", "10.2.2.2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"code\":\"1234\"}")
                .exchange()
                .expectStatus().is2xxSuccessful();
    }
}
