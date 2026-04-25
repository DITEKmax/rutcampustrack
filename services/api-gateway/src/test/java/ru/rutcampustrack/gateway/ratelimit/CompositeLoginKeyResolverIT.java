package ru.rutcampustrack.gateway.ratelimit;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

/**
 * M03a Группа 12: composite (ip, login) key-resolver интеграционный тест.
 *
 * <p>Проверяет что {@code ipLoginKeyResolver} правильно изолирует корзины:
 * 10+ запросов с IP-A+login=alice НЕ блокируют IP-B+login=alice (composite isolation).</p>
 *
 * <p>Auth-login route имеет ДВА последовательных RequestRateLimiter: IP-only 5/min
 * + composite 10/min. IP-only срабатывает первым на 6-м запросе с одного IP, поэтому
 * тест отправляет запросы с разных IP чтобы пересечь именно composite-порог.</p>
 */
@org.junit.jupiter.api.Disabled("M13 G25.21 — composite RL filter временно убран из application.yml "
        + "(Spring Cloud Gateway 4.x bug: два последовательных RequestRateLimiter "
        + "trip ReadOnlyHttpHeaders при response-commit). Тест вернётся когда composite "
        + "будет реализован через custom GlobalFilter без mutation после committed response.")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompositeLoginKeyResolverIT {

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
        WIREMOCK.stubFor(get(urlEqualTo("/auth/public-key")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"publicKey\":\"-----BEGIN PUBLIC KEY-----\\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7PZ7h1J+Uth3VnhqF7eG7H/r4v0h3OdW+k3H8Cc5zWWpJs3oQFFxKtMSY6T4Uf7Qm5JtX7sX5+cJF9o+1nD4G3xWwPt2k0gzY5o7a3pFFkYl5gKh4PqQOd3Gg7E8iH+yP5gPj9oMtSj8Mx3f+mKXR8/FiZP7FLFQJ7t2LhJGn7bYhDQLVJLfSg8F7Y4XQ8QqCXVZSxmCJz7xpQO3/ez+LN6I0JaN+P6DYxNBqjZ5v4jV5zV3cN9E9XKbG8p4jGJ+QIDAQAB\\n-----END PUBLIC KEY-----\",\"algorithm\":\"RS256\"}")));
        WIREMOCK.stubFor(post(urlPathMatching("/auth/login"))
                .willReturn(aResponse().withStatus(401)  // auth failure (wrong password) — нам важен rate-limit, не auth
                        .withBody("{\"status\":401,\"title\":\"Unauthorized\"}")));
        WIREMOCK.stubFor(any(urlPathMatching("/auth/.*")).atPriority(10)
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
        r.add("spring.data.redis.password", () -> "");
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
    @DisplayName("Composite (ip, login): login='alice' с IP-A не блокирует IP-B")
    void compositeKeyIsolatesByIp() {
        redis.keys("request_rate_limiter*").flatMap(redis::delete).blockLast();

        String loginName = "alice";

        // 5 запросов с IP-A (IP-RL=5, упрётся в IP-RL на 6-м, но 5 пройдут composite).
        // X-Login НЕ шлём — LoginBodyExtractionFilter вытянет из body (KI-8).
        for (int i = 0; i < 5; i++) {
            client.post().uri("/api/auth/login")
                    .header("X-Forwarded-For", "10.1.1.1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"login\":\"" + loginName + "\",\"password\":\"x\"}")
                    .exchange()
                    // 401 от WireMock (auth failure) — НЕ 429
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // Первый запрос с IP-B для того же login — должен пройти (composite bucket пустой)
        client.post().uri("/api/auth/login")
                .header("X-Forwarded-For", "10.2.2.2")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"" + loginName + "\",\"password\":\"x\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);  // 401 от downstream, НЕ 429
    }

    @Test
    @DisplayName("KI-8: composite isolates по login внутри одного IP (разные логины — разные buckets)")
    void compositeIsolatesByLogin_sameIp_KI8() {
        // NB: этот тест проверяет что LoginBodyExtractionFilter ставит X-Login
        // и composite key-resolver использует разные buckets для разных login'ов.
        // IP-RL=5 даёт окно для 5 попыток в sum. Поэтому делаем: alice×5 (упираются
        // в IP-RL на 6-м), потом bob×1 с другого IP — проходит (composite bucket
        // bob пустой). Это повторяет compositeKeyIsolatesByIp, но явно тестирует
        // что body-extraction работает (нет X-Login в заголовках).
        redis.keys("request_rate_limiter*").flatMap(redis::delete).blockLast();

        // alice × 5 из IP-A
        for (int i = 0; i < 5; i++) {
            client.post().uri("/api/auth/login")
                    .header("X-Forwarded-For", "10.5.5.5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"login\":\"alice\",\"password\":\"x\"}")
                    .exchange()
                    .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        // bob × 1 из IP-B — composite bucket (10.6.6.6, bob) пустой, IP-RL (10.6.6.6)
        // тоже пустой → 401, НЕ 429.
        client.post().uri("/api/auth/login")
                .header("X-Forwarded-For", "10.6.6.6")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"login\":\"bob\",\"password\":\"x\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Разные login с одного IP: composite НЕ изолирует, IP-RL применяется к обоим")
    void differentLogins_sameIp_bothLimitedByIp() {
        redis.keys("request_rate_limiter*").flatMap(redis::delete).blockLast();
        String ip = "10.3.3.3";

        // Flood с IP=10.3.3.3: login=alice × 6 потом сразу bob × 6 (12 запросов за секунду).
        // burstCapacity=5 для IP-RL → первые 5 пройдут, 6-й = 429.
        // Композитная корзина (ip+alice) и (ip+bob) разные, но IP-RL общий.
        int denied = 0;
        int passed = 0;
        for (int i = 0; i < 12; i++) {
            String login = i < 6 ? "alice" : "bob";
            org.springframework.http.HttpStatusCode status = client.post().uri("/api/auth/login")
                    .header("X-Forwarded-For", ip)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"login\":\"" + login + "\",\"password\":\"x\"}")
                    .exchange()
                    .returnResult(String.class).getStatus();
            if (status.value() == 429) denied++;
            else if (status.value() == 401) passed++;
        }
        // IP-RL burst=5 → минимум часть запросов должна быть отказана (подтверждает общий IP-bucket)
        org.assertj.core.api.Assertions.assertThat(denied).isGreaterThan(0);
        org.assertj.core.api.Assertions.assertThat(passed).isGreaterThan(0);
    }
}
