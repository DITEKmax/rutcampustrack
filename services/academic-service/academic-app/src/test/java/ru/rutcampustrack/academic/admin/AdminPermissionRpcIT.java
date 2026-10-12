package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.shared.security.*;
import ru.rutcampustrack.shared.security.admin.*;

class AdminPermissionRpcIT {
    @Test
    void signedIdentityFreshDenyForgedIdentityAndUnavailableCannotGrantFull() throws Exception {
        var signer = new InternalJwtTestFactory();
        var provider = mock(PublicKeyProvider.class);
        when(provider.getPublicKey()).thenReturn(signer.publicKey());
        var validator =
                new InternalJwtValidator(
                        provider, new InternalJwtProperties(null, 60, 0, false, null, null, null));
        var authority = mock(AdminAccessJdbcAuthority.class);
        var service = mock(AcademicGrpcServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "adminAuthority", authority);
        Server server =
                ServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        service,
                                        new StudentHomeworkGrpcIdentityInterceptor(validator)))
                        .build()
                        .start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var base =
                    AcademicGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(3, TimeUnit.SECONDS);
            var request = AdminPermissionCheckRequest.newBuilder().setFullOnly(true).build();
            when(authority.current(any()))
                    .thenReturn(
                            new AdminAccessProfile(1, true, "FULL", AdminPermission.allCodes(), 1));
            var token =
                    signer.validToken(
                            1, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false);
            var accepted = signed(base, token);
            assertThat(accepted.checkAdminPermission(request).getAllowed()).isTrue();
            when(authority.current(any()))
                    .thenReturn(
                            new AdminAccessProfile(1, true, "LIMITED", List.of("USERS_VIEW"), 2));
            assertThat(accepted.checkAdminPermission(request).getAllowed()).isFalse();
            assertThat(
                            accepted.checkAdminPermission(
                                            AdminPermissionCheckRequest.newBuilder()
                                                    .addPermissions("UNKNOWN")
                                                    .build())
                                    .getAllowed())
                    .isFalse();
            assertThatThrownBy(() -> base.checkAdminPermission(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            var forged =
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
            assertThatThrownBy(() -> signed(base, forged).checkAdminPermission(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            when(authority.current(any())).thenThrow(AdminAccessException.unavailable());
            assertThatThrownBy(() -> accepted.checkAdminPermission(request))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(((StatusRuntimeException) e).getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAVAILABLE));
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub signed(
            AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub, String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), token);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
