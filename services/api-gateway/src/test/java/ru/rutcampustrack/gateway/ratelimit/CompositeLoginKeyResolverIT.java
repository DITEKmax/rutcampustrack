package ru.rutcampustrack.gateway.ratelimit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * M03a Группа 12: composite (ip, login) key-resolver интеграционный тест.
 *
 * <p>Проверяет что {@code ipLoginKeyResolver} правильно изолирует корзины: 10+ запросов с
 * IP-A+login=alice НЕ блокируют IP-B+login=alice (composite isolation).
 *
 * <p>Auth-login route имеет ДВА последовательных RequestRateLimiter: IP-only 5/min + composite
 * 10/min. IP-only срабатывает первым на 6-м запросе с одного IP, поэтому тест отправляет запросы с
 * разных IP чтобы пересечь именно composite-порог.
 */
@org.junit.jupiter.api.Disabled(
        "M13 G25.21 — composite RL filter временно убран из application.yml (Spring Cloud Gateway"
            + " 4.x bug: два последовательных RequestRateLimiter trip ReadOnlyHttpHeaders при"
            + " response-commit). Тест вернётся когда composite будет реализован через custom"
            + " GlobalFilter без mutation после committed response.")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompositeLoginKeyResolverIT {

    // M08 D5 — reuse=true; локально через ~/.testcontainers.properties.
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(true);

    static WireMockServer WIREMOCK;

    @Autowired WebTestClient client;

    @Autowired ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void startInfra() {
        REDIS.start();
        WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());
        WIREMOCK.start();
        WIREMOCK.stubFor(
                get(urlEqualTo("/auth/public-key"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"publicKey\":\"-----BEGIN PUBLIC KEY-----\\n"
                                                    + "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7PZ7h1J+Uth3VnhqF7eG7H/r4v0h3OdW+k3H8Cc5zWWpJs3oQFFxKtMSY6T4Uf7Qm5JtX7sX5+cJF9o+1nD4G3xWwPt2k0gzY5o7a3pFFkYl5gKh4PqQOd3Gg7E8iH+yP5gPj9oMtSj8Mx3f+mKXR8/FiZP7FLFQJ7t2LhJGn7bYhDQLVJLfSg8F7Y4XQ8QqCXVZSxmCJz7xpQO3/ez+LN6I0JaN+P6DYxNBqjZ5v4jV5zV3cN9E9XKbG8p4jGJ+QIDAQAB\\n"
                                                    + "-----END PUBLIC"
                                                    + " KEY-----\",\"algorithm\":\"RS256\"}")));
        WIREMOCK.stubFor(
                post(urlPathMatching("/auth/login"))
                        .willReturn(
                                aResponse()
                                        .withStatus(
                                                401) // auth failure (wrong password) — нам важен
                                        // rate-limit, не auth
                                        .withBody("{\"status\":401,\"title\":\"Unauthorized\"}")));
        WIREMOCK.stubFor(
                any(urlPathMatching("/auth/.*"))
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
        r.add("spring.data.redis.password", () -> "");
        r.add("gateway.auth-service-url", () -> "http://localhost:" + WIREMOCK.port());
        r.add(
                "rutcampustrack.security.internal-issuer-client.auth-service-url",
                () -> "http://localhost:" + WIREMOCK.port());
        r.add("AUTH_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ACADEMIC_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("SCHEDULE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ATTENDANCE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("NOTIFICATION_WEB_URL", () -> "http://localhost:" + WIREMOCK.port());
    }
}
