package ru.rutcampustrack.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class InternalJwtIssuerClientTest {

    private WireMockServer server;
    private InternalJwtIssuerClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();

        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setAuthServiceUrl("http://localhost:" + server.port());
        props.setSecret("a".repeat(32));
        props.setCacheTtlSeconds(240);
        // M13 G24-fix-7: bumped 2_000 → 10_000 для устранения flaky failure
        // на холодных CI runner'ах. WireMock startup + first WebClient request
        // могут занимать > 2s в первом тесте Spring suite, что давало
        // InternalIssuerUnavailableException вместо expected token. 10s
        // достаточно даже для slow runners; production timeout (3s) —
        // отдельный property в application.yml.
        props.setTimeoutMillis(10_000);

        WebClient webClient = WebClient.builder().baseUrl(props.getAuthServiceUrl()).build();
        client = new InternalJwtIssuerClient(props, webClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void firstCall_reachesAuthService_andReturnsToken() {
        stubIssue("fake-jwt-token", 1);

        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true))
                .expectNext("fake-jwt-token")
                .verifyComplete();

        server.verify(1, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt"))
                .withHeader("X-Internal-Issuer-Secret", equalTo("a".repeat(32))));
    }

    @Test
    void secondCall_sameUser_hitsCacheNotNetwork() {
        stubIssue("token-1", 10);

        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true)).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true)).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true)).expectNextCount(1).verifyComplete();

        // Despite 3 issueFor calls, auth-service is hit only once (cache hit for subsequent).
        server.verify(1, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    @Test
    void differentUsers_getSeparateCacheEntries() {
        stubIssue("token-X", 10);

        StepVerifier.create(client.issueFor(1L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.issueFor(2L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.issueFor(3L, "STUDENT", 99L, false)).expectNextCount(1).verifyComplete();

        server.verify(3, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
        assertThat(client.estimatedSize()).isEqualTo(3);
    }

    @Test
    void sameUser_roleChange_getsNewToken() {
        stubIssue("token-role", 10);

        StepVerifier.create(client.issueFor(42L, "STUDENT", null, false)).expectNextCount(1).verifyComplete();
        StepVerifier.create(client.issueFor(42L, "STUDENT", null, false)).expectNextCount(1).verifyComplete();
        // Role changed → different cache key
        StepVerifier.create(client.issueFor(42L, "TEACHER", null, false)).expectNextCount(1).verifyComplete();

        server.verify(2, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    @Test
    void authService500_propagatesAsUnavailable() {
        server.stubFor(post(urlEqualTo("/internal/issue-internal-jwt"))
                .willReturn(WireMock.aResponse().withStatus(500).withBody("upstream down")));

        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true))
                .expectError(InternalIssuerUnavailableException.class)
                .verify();
    }

    @Test
    void authService401_propagatesAsUnavailable() {
        // Gateway misconfigured secret — auth-service returns 401
        server.stubFor(post(urlEqualTo("/internal/issue-internal-jwt"))
                .willReturn(WireMock.aResponse().withStatus(401).withBody("{\"error\":\"bad secret\"}")));

        StepVerifier.create(client.issueFor(42L, "ADMIN", 7L, true))
                .expectError(InternalIssuerUnavailableException.class)
                .verify();
    }

    @Test
    void cachedToken_nearExpiry_triggersReIssue_KI3() {
        // M03b Группа 9 (KI-3): токен с expiresAt через 3 секунды < порога 5s —
        // issueFor детектит это СРАЗУ внутри первого вызова, инвалидирует
        // ключ и перевыдаёт. В stub'е оба ответа near-expiry → поэтому
        // каждый issueFor делает 2 сетевых вызова. Ожидаемое поведение:
        // fail-safe retry при clock-drift edge case.
        stubIssueWithExpiry("near-expiry-token", Instant.now().plusSeconds(3));

        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false))
                .expectNext("near-expiry-token")
                .verifyComplete();

        // Первый issueFor: получил near-expiry → invalidate → retry → снова
        // near-expiry (stub тот же). Итого 2 сетевых запроса за один issueFor.
        server.verify(2, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));

        // Теперь поменяем stub на healthy-token — повторный issueFor видит
        // near-expiry в кэше, invalidate, сходит за свежим (+1 запрос).
        // Свежий кэшируется нормально.
        stubIssue("fresh-token", 10);
        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false))
                .expectNext("fresh-token")
                .verifyComplete();
        server.verify(3, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));

        // Следующий issueFor — cache hit, без сетевого вызова.
        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false))
                .expectNext("fresh-token")
                .verifyComplete();
        server.verify(3, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    @Test
    void invalidateAll_forcesRefetch() {
        stubIssue("token-inv", 10);

        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();
        client.invalidateAll();
        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();

        server.verify(2, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    private void stubIssue(String token, int ttlMinutes) {
        stubIssueWithExpiry(token, Instant.now().plusSeconds(ttlMinutes * 60L));
    }

    private void stubIssueWithExpiry(String token, Instant expiresAt) {
        String body = "{\"token\":\"" + token + "\","
                + "\"expiresAt\":\"" + expiresAt + "\"}";
        server.stubFor(post(urlEqualTo("/internal/issue-internal-jwt"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
