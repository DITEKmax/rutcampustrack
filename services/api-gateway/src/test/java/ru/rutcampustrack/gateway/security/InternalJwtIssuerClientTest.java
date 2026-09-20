package ru.rutcampustrack.gateway.security;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class InternalJwtIssuerClientTest {

    private static final String ACCESS_TOKEN = "signed-access-token";
    private static final String ADMISSION_RESPONSE = """
            {
              "internalToken":"internal.jwt.token",
              "expiresAt":"2026-09-10T20:00:00Z",
              "sessionId":"00000000-0000-0000-0000-000000000001",
              "userId":"42",
              "sessionVersion":"7",
              "rolesVersion":"3",
              "role":"HEADMAN",
              "status":"ACTIVE",
              "groupId":"8",
              "isHeadman":true,
              "readOnly":false
            }
            """;

    private WireMockServer server;
    private InternalJwtIssuerClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();

        InternalIssuerClientProperties props = new InternalIssuerClientProperties();
        props.setAuthServiceUrl("http://localhost:" + server.port());
        props.setSecret("a".repeat(32));
        props.setTimeoutMillis(10_000);
        client = new InternalJwtIssuerClient(props,
                WebClient.builder().baseUrl(props.getAuthServiceUrl()).build());
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void eachCall_reachesAdmission_andBodyContainsOnlyAccessToken() {
        server.stubFor(post(urlEqualTo("/internal/auth/admit"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ADMISSION_RESPONSE)));

        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .assertNext(response -> assertThat(response.userId()).isEqualTo("42"))
                .verifyComplete();
        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .assertNext(response -> assertThat(response.role()).isEqualTo("HEADMAN"))
                .verifyComplete();

        server.verify(2, postRequestedFor(urlEqualTo("/internal/auth/admit"))
                .withHeader("X-Internal-Issuer-Secret", equalTo("a".repeat(32)))
                .withRequestBody(equalToJson("{\"accessToken\":\"" + ACCESS_TOKEN + "\"}")));
    }

    @Test
    void acceptedDenialCodes_areMappedWithoutUpstreamDetail() {
        server.stubFor(post(urlEqualTo("/internal/auth/admit"))
                .willReturn(aResponse().withStatus(401)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":401,\"extras\":{\"code\":\"SESSION_REVOKED\"},"
                                + "\"detail\":\"secret upstream detail\"}")));

        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(InternalAdmissionDeniedException.class);
                    InternalAdmissionDeniedException denied = (InternalAdmissionDeniedException) error;
                    assertThat(denied.publicStatus().value()).isEqualTo(401);
                    assertThat(denied.publicCode()).isEqualTo("INVALID_SESSION");
                    assertThat(denied.getMessage()).doesNotContain("secret upstream detail");
                })
                .verify();
    }

    @Test
    void statusOnlyOrUnknownError_isDependencyUnavailable() {
        server.stubFor(post(urlEqualTo("/internal/auth/admit"))
                .willReturn(aResponse().withStatus(403)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":403,\"detail\":\"ROLE_NOT_GRANTED\"}")));

        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .expectError(InternalIssuerUnavailableException.class)
                .verify();
    }

    @Test
    void authorityUnavailableCode_isDependencyUnavailable() {
        server.stubFor(post(urlEqualTo("/internal/auth/admit"))
                .willReturn(aResponse().withStatus(503)
                        .withHeader("Content-Type", "application/problem+json")
                        .withBody("{\"status\":503,\"extras\":{\"code\":\"AUTHORITY_UNAVAILABLE\"}}")));

        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .expectError(InternalIssuerUnavailableException.class)
                .verify();
    }

    @Test
    void malformedOrAlternateTypedSuccess_isDependencyUnavailable() {
        server.stubFor(post(urlEqualTo("/internal/auth/admit"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"internalToken\":7,\"expiresAt\":\"2026-09-10T20:00:00Z\","
                                + "\"sessionId\":\"00000000-0000-0000-0000-000000000001\","
                                + "\"userId\":\"42\",\"sessionVersion\":\"7\","
                                + "\"rolesVersion\":\"3\",\"role\":\"STUDENT\","
                                + "\"status\":\"ACTIVE\",\"isHeadman\":false,\"readOnly\":false}")));

        StepVerifier.create(client.admit(ACCESS_TOKEN))
                .expectError(InternalIssuerUnavailableException.class)
                .verify();
    }
}
