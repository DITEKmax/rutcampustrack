package ru.rutcampustrack.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * M03a Группа 13 (14 P1-1): end-to-end contract-тест Gateway↔downstream
 * Internal JWT pipeline.
 *
 * <p>Flow: клиент → Gateway (с валидным внешним JWT) → JwtAuthenticationFilter
 * валидирует подписью → InternalJwtIssuerFilter (через WireMock auth-service)
 * → issued Internal JWT через X-Internal-Token → downstream stub.</p>
 *
 * <p>Проверяет инварианты:
 * <ul>
 *   <li>Валидный внешний JWT → downstream получает X-Internal-Token.</li>
 *   <li>Невалидная подпись внешнего JWT → 401, downstream НЕ вызывается.</li>
 *   <li>Истёкший внешний JWT → 401, downstream НЕ вызывается.</li>
 *   <li>Отсутствие Authorization header → 401.</li>
 * </ul>
 */
// M14 G9: @ActiveProfiles("test") нужен для RequiredSecretsValidator
// (G4 v2) skip — IT использует WireMock auth-service, REDIS_PASSWORD/
// INTERNAL_ISSUER_SECRET не выставлены в env (DynamicPropertySource
// override-ит только нужные конкретно для теста props).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalJwtIssuerIT {

    private static KeyPair externalKeyPair;
    private static String publicKeyPem;
    static WireMockServer WIREMOCK;

    @Autowired
    WebTestClient client;

    @BeforeAll
    static void startInfra() throws NoSuchAlgorithmException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        externalKeyPair = gen.generateKeyPair();

        byte[] encoded = externalKeyPair.getPublic().getEncoded();
        publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded)
                + "\n-----END PUBLIC KEY-----";

        WIREMOCK = new WireMockServer(wireMockConfig().dynamicPort());
        WIREMOCK.start();

        // Auth-service public key — тот же ключ которым подписываются внешние JWT
        WIREMOCK.stubFor(get(urlEqualTo("/auth/public-key")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"publicKey\":" + jsonString(publicKeyPem) + ",\"algorithm\":\"RS256\"}")));

        // Auth-service token exchange — возвращает "fake-internal-token" плюс эхо claims
        WIREMOCK.stubFor(post(urlEqualTo("/internal/issue-internal-jwt"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"fake-internal-token-42\",\"expiresAt\":9999999999}")));

        // Downstream stub — /api/attendance/* echo query (возвращает 200)
        WIREMOCK.stubFor(any(urlPathMatching("/attendance/.*"))
                .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));
        WIREMOCK.stubFor(any(urlPathMatching("/.*")).atPriority(20)
                .willReturn(aResponse().withStatus(200).withBody("{}")));
    }

    @AfterAll
    static void stopInfra() {
        if (WIREMOCK != null) WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("gateway.auth-service-url", () -> "http://localhost:" + WIREMOCK.port());
        r.add("rutcampustrack.security.internal-issuer-client.auth-service-url",
                () -> "http://localhost:" + WIREMOCK.port());
        r.add("AUTH_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ACADEMIC_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("SCHEDULE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("ATTENDANCE_SERVICE_URL", () -> "http://localhost:" + WIREMOCK.port());
        r.add("NOTIFICATION_WEB_URL", () -> "http://localhost:" + WIREMOCK.port());
        // Rate-limit в этих тестах не тестируется — Redis отсутствует, fail-open пропустит
        r.add("spring.data.redis.host", () -> "127.0.0.1");
        r.add("spring.data.redis.port", () -> 1);
        r.add("spring.data.redis.password", () -> "");
        r.add("spring.data.redis.timeout", () -> "500ms");
        r.add("spring.data.redis.connect-timeout", () -> "500ms");
    }

    @Test
    @DisplayName("Валидный внешний JWT → downstream вызван с X-Internal-Token")
    void validExternalJwt_downstreamReceivesInternalToken() {
        WIREMOCK.resetRequests();
        String jwt = signExternalJwt(42L, "STUDENT", 7L, false,
                new Date(System.currentTimeMillis() + 60_000));

        client.get().uri("/api/attendance/reports/student/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk();

        // Downstream WireMock получил X-Internal-Token с токеном от auth-service
        RequestPatternBuilder downstreamCall = postRequestedFor(urlPathMatching("/attendance/.*"))
                .withHeader("X-Internal-Token", com.github.tomakehurst.wiremock.client.WireMock
                        .equalTo("fake-internal-token-42"));
        List<com.github.tomakehurst.wiremock.stubbing.ServeEvent> events = WIREMOCK.getAllServeEvents();
        boolean found = events.stream().anyMatch(ev -> {
            String path = ev.getRequest().getUrl();
            return path.startsWith("/attendance/") && ev.getRequest().getHeader("X-Internal-Token") != null
                    && ev.getRequest().getHeader("X-Internal-Token").equals("fake-internal-token-42");
        });
        assertThat(found).as("downstream должен получить X-Internal-Token=fake-internal-token-42").isTrue();

        // Token-exchange был вызван хотя бы 1 раз
        WIREMOCK.verify(com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly(1),
                postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    @Test
    @DisplayName("Невалидная подпись внешнего JWT → 401, downstream НЕ вызывается")
    void invalidSignature_returns401_downstreamNotCalled() throws NoSuchAlgorithmException {
        WIREMOCK.resetRequests();
        // Подписан другой парой ключей
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair rogue = gen.generateKeyPair();
        String jwt = Jwts.builder()
                .subject("42")
                .issuer("rutcampustrack-auth")
                .audience().add("rutcampustrack").and()
                .claim("role", "STUDENT")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(rogue.getPrivate())
                .compact();

        client.get().uri("/api/attendance/reports/student/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);

        // Downstream /attendance/* НЕ вызывался
        WIREMOCK.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(urlPathMatching("/attendance/.*")));
        // Token-exchange НЕ вызывался (Gateway остановился на JwtAuthFilter)
        WIREMOCK.verify(0, postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    @Test
    @DisplayName("Истёкший внешний JWT → 401, downstream НЕ вызывается")
    void expiredJwt_returns401_downstreamNotCalled() {
        WIREMOCK.resetRequests();
        String jwt = signExternalJwt(42L, "STUDENT", 7L, false,
                new Date(System.currentTimeMillis() - 10_000));  // 10s в прошлом

        client.get().uri("/api/attendance/reports/student/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

        WIREMOCK.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(urlPathMatching("/attendance/.*")));
    }

    @Test
    @DisplayName("Отсутствие Authorization header → 401")
    void noAuthHeader_returns401() {
        WIREMOCK.resetRequests();

        client.get().uri("/api/attendance/reports/student/stats")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED);

        WIREMOCK.verify(0, com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(urlPathMatching("/attendance/.*")));
    }

    @Test
    @DisplayName("DOCX export binary response is proxied without JSON/error rewriting")
    void headmanWeeklyDocxDownload_binaryResponsePassesThroughGateway() {
        WIREMOCK.resetRequests();
        byte[] docxBytes = new byte[]{'P', 'K', 3, 4, 1, 2, 3};
        WIREMOCK.stubFor(get(urlEqualTo("/attendance/reports/headman-weekly/current?weekStart=2026-04-27&format=docx"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE,
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .withHeader(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''UVPV511_27.04.2026_03.05.2026.docx")
                        .withBody(docxBytes)));
        String jwt = signExternalJwt(42L, "STUDENT", 7L, true,
                new Date(System.currentTimeMillis() + 60_000));

        client.get().uri("/api/attendance/reports/headman-weekly/current?weekStart=2026-04-27&format=docx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''UVPV511_27.04.2026_03.05.2026.docx")
                .expectBody(byte[].class)
                .value(body -> assertThat(body).containsExactly(docxBytes));
    }

    private String signExternalJwt(long userId, String role, Long groupId, boolean isHeadman, Date expiry) {
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer("rutcampustrack-auth")
                .audience().add("rutcampustrack").and()
                .claim("role", role)
                .claim("is_headman", isHeadman)
                .expiration(expiry);
        if (groupId != null) builder.claim("group_id", groupId);
        return builder.signWith((RSAPrivateKey) externalKeyPair.getPrivate()).compact();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
