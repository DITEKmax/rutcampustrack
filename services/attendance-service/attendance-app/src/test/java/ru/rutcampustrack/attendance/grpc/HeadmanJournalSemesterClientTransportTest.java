package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.HeadmanJournalSemesterStateRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtProperties;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.PublicKeyProvider;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

/** Real client serialization, original signed actor forwarding, and typed HTTP error boundaries. */
class HeadmanJournalSemesterClientTransportTest {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Context.Key<InternalJwtClaims> ACTOR = Context.key("journal-test-actor");

    @Test
    void originalSignedActorReachesNewMethodAndAdmissionFailuresKeepTheirHttpMeaning()
            throws Exception {
        var signer = new InternalJwtTestFactory();
        var provider = mock(PublicKeyProvider.class);
        when(provider.getPublicKey()).thenReturn(signer.publicKey());
        var validator =
                new InternalJwtValidator(
                        provider, new InternalJwtProperties(null, 60, 0, false, null, null, null));
        var received = new AtomicReference<String>();
        var failure = new AtomicReference<Status>();
        var calls = new AtomicInteger();
        var responseId = new AtomicLong(3);
        ServerInterceptor identity =
                new ServerInterceptor() {
                    @Override
                    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                            ServerCall<ReqT, RespT> call,
                            Metadata headers,
                            ServerCallHandler<ReqT, RespT> next) {
                        try {
                            received.set(headers.get(TOKEN));
                            var actor = validator.validate(headers.get(TOKEN));
                            return Contexts.interceptCall(
                                    Context.current().withValue(ACTOR, actor), call, headers, next);
                        } catch (InternalJwtException invalid) {
                            call.close(Status.UNAUTHENTICATED, new Metadata());
                            return new ServerCall.Listener<>() {};
                        }
                    }
                };
        var service =
                new AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase() {
                    @Override
                    public void getHeadmanJournalSemesterState(
                            HeadmanJournalSemesterStateRequest request,
                            StreamObserver<SemesterStateResponse> observer) {
                        calls.incrementAndGet();
                        assertThat(ACTOR.get().role()).isEqualTo("HEADMAN");
                        assertThat(ACTOR.get().userId()).isEqualTo(41);
                        assertThat(ACTOR.get().groupId()).isEqualTo(7);
                        assertThat(request.getGroupId()).isEqualTo(7);
                        assertThat(request.getSemesterId()).isEqualTo(3);
                        if (failure.get() != null) {
                            observer.onError(failure.get().asRuntimeException());
                            return;
                        }
                        observer.onNext(
                                SemesterStateResponse.newBuilder().setId(responseId.get()).build());
                        observer.onCompleted();
                    }
                };
        Server server =
                NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                        .addService(
                                ServerInterceptors.intercept(
                                        service,
                                        identity,
                                        new ServiceIdentityServerInterceptor(
                                                ServicePrincipal.ACADEMIC_SERVICE,
                                                Map.of(
                                                        ServiceIdentityServerInterceptor
                                                                .ACADEMIC_GET_SEMESTER_STATE,
                                                        ServicePrincipal.SCHEDULE_SERVICE),
                                                List.of())))
                        .build()
                        .start();
        ManagedChannel channel =
                NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var requestContext = mock(RequestContext.class);
            String token =
                    signer.validToken(
                            41, UUID.randomUUID(), 1, 1, "HEADMAN", "ACTIVE", 7L, true, false);
            when(requestContext.getInternalToken()).thenReturn(token);
            var client = new AcademicGrpcClient();
            ReflectionTestUtils.setField(client, "requestContext", requestContext);
            ReflectionTestUtils.setField(
                    client, "stub", AcademicGrpcServiceGrpc.newBlockingStub(channel));
            assertThat(client.getHeadmanJournalSemesterState(7, 3).getId()).isEqualTo(3);
            // Compare only a boolean so a failed assertion cannot print the signed credential.
            assertThat(
                            MessageDigest.isEqual(
                                    received.get().getBytes(StandardCharsets.UTF_8),
                                    token.getBytes(StandardCharsets.UTF_8)))
                    .isTrue();
            responseId.set(4);
            assertThatThrownBy(() -> client.getHeadmanJournalSemesterState(7, 3))
                    .isInstanceOf(AcademicServiceUnavailableException.class);
            responseId.set(3);
            when(requestContext.getInternalToken()).thenReturn(null);
            assertThatThrownBy(() -> client.getHeadmanJournalSemesterState(7, 3))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
            assertThat(calls).hasValue(2);
            when(requestContext.getInternalToken())
                    .thenReturn(
                            new InternalJwtTestFactory()
                                    .validToken(
                                            41,
                                            UUID.randomUUID(),
                                            1,
                                            1,
                                            "HEADMAN",
                                            "ACTIVE",
                                            7L,
                                            true,
                                            false));
            assertThatThrownBy(() -> client.getHeadmanJournalSemesterState(7, 3))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
            assertThat(calls).hasValue(2);
            when(requestContext.getInternalToken()).thenReturn(token);
            failure.set(Status.UNAUTHENTICATED);
            assertThatThrownBy(() -> client.getHeadmanJournalSemesterState(7, 3))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
            for (var code : new Status.Code[] {Status.Code.INVALID_ARGUMENT, Status.Code.NOT_FOUND,
                    Status.Code.PERMISSION_DENIED, Status.Code.UNAVAILABLE}) {
                failure.set(Status.fromCode(code));
                Class<? extends RuntimeException> expected =
                        switch (code) {
                            case INVALID_ARGUMENT -> BadRequestException.class;
                            case NOT_FOUND -> ResourceNotFoundException.class;
                            case PERMISSION_DENIED -> AccessDeniedException.class;
                            default -> AcademicServiceUnavailableException.class;
                        };
                assertThatThrownBy(() -> client.getHeadmanJournalSemesterState(7, 3))
                        .isInstanceOf(expected);
            }
        } finally {
            received.set(null);
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
