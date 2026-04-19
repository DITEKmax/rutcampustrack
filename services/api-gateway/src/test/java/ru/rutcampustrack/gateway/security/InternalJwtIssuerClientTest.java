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
        props.setTimeoutMillis(2_000);

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
    void invalidateAll_forcesRefetch() {
        stubIssue("token-inv", 10);

        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();
        client.invalidateAll();
        StepVerifier.create(client.issueFor(42L, "ADMIN", null, false)).expectNextCount(1).verifyComplete();

        server.verify(2, WireMock.postRequestedFor(urlEqualTo("/internal/issue-internal-jwt")));
    }

    private void stubIssue(String token, int ttlMinutes) {
        String body = "{\"token\":\"" + token + "\","
                + "\"expiresAt\":\"" + Instant.now().plusSeconds(ttlMinutes * 60L) + "\"}";
        server.stubFor(post(urlEqualTo("/internal/issue-internal-jwt"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
