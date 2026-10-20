package ru.rutcampustrack.mobilebff.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.grpc.*;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.mobilebff.MobileBffApplication;
import ru.rutcampustrack.shared.security.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(
        classes = MobileBffApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MapHttpGrpcIT {
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static Server server;
    @Autowired private TestRestTemplate http;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @MockitoBean private PublicKeyProvider publicKeyProvider;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        var keyProvider = org.mockito.Mockito.mock(PublicKeyProvider.class);
        when(keyProvider.getPublicKey()).thenReturn(JWT.publicKey());
        var validator =
                new InternalJwtValidator(
                        keyProvider,
                        new InternalJwtProperties(null, 60, 0, false, null, null, null));
        server =
                ServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        new AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase() {
                                            @Override
                                            public void getCampusMapManifest(
                                                    CampusMapManifestRequest request,
                                                    StreamObserver<CampusMapManifestResponse>
                                                            observer) {
                                                CALLS.incrementAndGet();
                                                var response =
                                                        CampusMapManifestResponse.newBuilder();
                                                if (request.getKnownRevision() == 7)
                                                    response.setUnchanged(
                                                            CampusMapManifestUnchanged.newBuilder()
                                                                    .setRevision(7));
                                                else
                                                    response.setManifest(
                                                            CampusMapManifest.newBuilder()
                                                                    .setSchemaVersion(1)
                                                                    .setValidationPolicyVersion(1)
                                                                    .setRevision(7));
                                                observer.onNext(response.build());
                                                observer.onCompleted();
                                            }
                                        },
                                        new ServerInterceptor() {
                                            @Override
                                            public <ReqT, RespT>
                                                    ServerCall.Listener<ReqT> interceptCall(
                                                            ServerCall<ReqT, RespT> call,
                                                            Metadata headers,
                                                            ServerCallHandler<ReqT, RespT> next) {
                                                var identity =
                                                        validator.validate(
                                                                headers.get(
                                                                        Metadata.Key.of(
                                                                                "x-internal-token",
                                                                                Metadata
                                                                                        .ASCII_STRING_MARSHALLER)));
                                                assertThat(identity.role())
                                                        .isIn("TEACHER", "HEADMAN");
                                                if (identity.role().equals("HEADMAN"))
                                                    assertThat(identity.groupId()).isEqualTo(11L);
                                                return next.startCall(call, headers);
                                            }
                                        }))
                        .build()
                        .start();
        registry.add(
                "grpc.client.academic-service.address",
                () -> "static://127.0.0.1:" + server.getPort());
        registry.add("grpc.client.academic-service.negotiation-type", () -> "plaintext");
        registry.add("grpc.server.port", () -> -1);
    }

    @BeforeEach
    void setup() {
        when(publicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
        CALLS.set(0);
    }

    @AfterAll
    static void stop() throws Exception {
        if (server != null) {
            server.shutdownNow();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void teacherAndHeadmanManifestUsePrivateCacheAndConditional304WithoutBody() throws Exception {
        for (String role : java.util.List.of("TEACHER", "HEADMAN")) {
            var headers = new HttpHeaders();
            headers.set(
                    "X-Internal-Token",
                    JWT.validToken(
                            101L,
                            UUID.randomUUID(),
                            1,
                            1,
                            role,
                            "ACTIVE",
                            role.equals("HEADMAN") ? 11L : null,
                            role.equals("HEADMAN"),
                            false));
            var response =
                    http.exchange(
                            "/api/v1/map/manifest",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getETag()).isEqualTo("\"7\"");
            assertThat(response.getHeaders().getCacheControl()).contains("private", "no-cache");
            assertThat(json.readTree(response.getBody()).path("revision").asText()).isEqualTo("7");
            headers.setIfNoneMatch("\"7\"");
            response =
                    http.exchange(
                            "/api/v1/map/manifest",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
            assertThat(response.getHeaders().getETag()).isEqualTo("\"7\"");
            assertThat(response.getHeaders().getCacheControl()).contains("private", "no-cache");
            assertThat(response.getBody()).isNullOrEmpty();
        }
        assertThat(CALLS).hasValue(4);
    }
}
