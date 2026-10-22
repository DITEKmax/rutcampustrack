package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AdminPermissionCheckRequest;
import ru.rutcampustrack.academic.grpc.AdminPermissionCheckResponse;
import ru.rutcampustrack.attendance.adminactivity.AdminActivityReadRepository;
import ru.rutcampustrack.shared.security.*;

/** Real signed gRPC transport and a fresh capability round trip on every read. */
class AdminActivityGrpcIT {
    @Test
    void signedJwtNeedsFreshDashboardCapabilityAndFailuresNeverReturnFakeZero() throws Exception {
        var signer = new InternalJwtTestFactory();
        var keys = mock(PublicKeyProvider.class);
        when(keys.getPublicKey()).thenReturn(signer.publicKey());
        var validator =
                new InternalJwtValidator(
                        keys, new InternalJwtProperties(null, 60, 0, false, null, null, null));
        AtomicReference<Status> authorityState = new AtomicReference<>(Status.OK);
        AtomicReference<String> forwardedToken = new AtomicReference<>();
        var permissionApi =
                new AcademicGrpcServiceGrpc.AcademicGrpcServiceImplBase() {
                    @Override
                    public void checkAdminPermission(
                            AdminPermissionCheckRequest query,
                            StreamObserver<AdminPermissionCheckResponse> observer) {
                        if (authorityState.get().getCode() == Status.Code.UNAVAILABLE) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            return;
                        }
                        assertThat(query.getPermissionsList()).containsExactly("DASHBOARD_VIEW");
                        assertThat(query.getFullOnly()).isFalse();
                        observer.onNext(
                                AdminPermissionCheckResponse.newBuilder()
                                        .setAllowed(authorityState.get().isOk())
                                        .build());
                        observer.onCompleted();
                    }
                };
        ServerInterceptor capture =
                new ServerInterceptor() {
                    @Override
                    public <Q, R> ServerCall.Listener<Q> interceptCall(
                            ServerCall<Q, R> call, Metadata headers, ServerCallHandler<Q, R> next) {
                        forwardedToken.set(
                                headers.get(
                                        Metadata.Key.of(
                                                "x-internal-token",
                                                Metadata.ASCII_STRING_MARSHALLER)));
                        return next.startCall(call, headers);
                    }
                };
        Server upstream =
                ServerBuilder.forPort(0)
                        .addService(ServerInterceptors.intercept(permissionApi, capture))
                        .build()
                        .start();
        ManagedChannel upstreamChannel =
                ManagedChannelBuilder.forAddress("127.0.0.1", upstream.getPort())
                        .usePlaintext()
                        .build();
        var authority = new AcademicAdminPermissionClient();
        ReflectionTestUtils.setField(
                authority, "stub", AcademicGrpcServiceGrpc.newBlockingStub(upstreamChannel));
        var repository = mock(AdminActivityReadRepository.class);
        when(repository.read(any()))
                .thenReturn(
                        AdminActivitySnapshot.newBuilder()
                                .setSummary(
                                        AdminActivityCounts.newBuilder()
                                                .setPositiveMarks(4)
                                                .setGeoMarks(2))
                                .build());
        Instant now = Instant.parse("2026-10-08T09:30:00Z");
        var service =
                new AdminActivityGrpcService(
                        authority, repository, Clock.fixed(now, ZoneOffset.UTC));
        Server server =
                ServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        service,
                                        new AdminActivityGrpcIdentityInterceptor(validator)))
                        .build()
                        .start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var base =
                    AttendanceAdminActivityGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS);
            var query =
                    AdminActivityQuery.newBuilder()
                            .setObservedAtEpochMs(now.toEpochMilli())
                            .setPeriod(AdminActivityPeriod.ADMIN_ACTIVITY_TODAY)
                            .setBucket(AdminActivityBucket.ADMIN_ACTIVITY_DAY)
                            .build();
            String token =
                    signer.validToken(
                            1, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false);
            var accepted = signed(base, token);
            assertThat(accepted.getActivity(query).getSummary().getPositiveMarks()).isEqualTo(4);
            assertThat(forwardedToken.get()).isEqualTo(token);
            authorityState.set(Status.PERMISSION_DENIED);
            assertCode(
                    () -> accepted.getActivity(query),
                    Status.Code.PERMISSION_DENIED); // same token, revoked live grant
            authorityState.set(Status.UNAVAILABLE);
            assertCode(() -> accepted.getActivity(query), Status.Code.UNAVAILABLE);
            assertCode(() -> base.getActivity(query), Status.Code.UNAUTHENTICATED);
            String forged =
                    new InternalJwtTestFactory()
                            .validToken(
                                    1,
                                    UUID.randomUUID(),
                                    1,
                                    1,
                                    "ADMIN",
                                    "ACTIVE",
                                    null,
                                    false,
                                    false);
            assertCode(() -> signed(base, forged).getActivity(query), Status.Code.UNAUTHENTICATED);
            String student =
                    signer.validToken(
                            2, UUID.randomUUID(), 1, 1, "STUDENT", "ACTIVE", 10L, false, false);
            assertCode(
                    () -> signed(base, student).getActivity(query), Status.Code.PERMISSION_DENIED);
            authorityState.set(Status.OK);
            assertCode(
                    () ->
                            accepted.getActivity(
                                    query.toBuilder()
                                            .setObservedAtEpochMs(
                                                    now.minusSeconds(61).toEpochMilli())
                                            .build()),
                    Status.Code.INVALID_ARGUMENT);
            verify(repository, times(1)).read(any());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            upstreamChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            upstream.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static AttendanceAdminActivityGrpcServiceGrpc
                    .AttendanceAdminActivityGrpcServiceBlockingStub
            signed(
                    AttendanceAdminActivityGrpcServiceGrpc
                                    .AttendanceAdminActivityGrpcServiceBlockingStub
                            stub,
                    String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static void assertCode(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable request, Status.Code code) {
        assertThatThrownBy(request)
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        error -> assertThat(Status.fromThrowable(error).getCode()).isEqualTo(code));
    }
}
