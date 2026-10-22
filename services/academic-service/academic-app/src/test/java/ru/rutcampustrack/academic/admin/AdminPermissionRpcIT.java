package ru.rutcampustrack.academic.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.grpc.AdminPermissionCheckRequest;
import ru.rutcampustrack.academic.grpc.StudentHomeworkGrpcIdentityInterceptor;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

class AdminPermissionRpcIT extends AbstractAcademicIntegrationTest {
    @Autowired private AcademicGrpcServiceImpl service;
    @Autowired private StudentHomeworkGrpcIdentityInterceptor identityInterceptor;
    @Autowired private AuthIdentityDataClient authority;
    @Autowired private AcademicUserIdentityOperations identityOperations;

    @Test
    void signedIdentityFreshDenyForgedIdentityAndUnavailableCannotGrantFull() throws Exception {
        var controller =
                identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        var target =
                identityFixture.seedUser(
                        new CreateUserRequest(
                                "Тестов",
                                "Администратор",
                                null,
                                UserRole.ADMIN,
                                null,
                                null,
                                null,
                                new AdminAccessInput("FULL", List.of())),
                        controller);
        var actor = identityFixture.select(target.userId(), AuthRole.ADMIN);
        Server server =
                ServerBuilder.forPort(0)
                        .addService(ServerInterceptors.intercept(service, identityInterceptor))
                        .build()
                        .start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var base =
                    AcademicGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS);
            var request = AdminPermissionCheckRequest.newBuilder().setFullOnly(true).build();
            var accepted = signed(base, actor.internalToken());
            assertThat(accepted.checkAdminPermission(request).getAllowed()).isTrue();
            try (var ignored = identityFixture.request(controller)) {
                identityOperations.adminAccess(
                        target.userId(),
                        new AdminAccessInput("LIMITED", List.of("USERS_VIEW")),
                        actor.profile().version(),
                        false);
            }
            assertThat(accepted.checkAdminPermission(request).getAllowed()).isFalse();
            var currentActor = identityFixture.select(target.userId(), AuthRole.ADMIN);
            var current = signed(base, currentActor.internalToken());
            assertThat(current.checkAdminPermission(request).getAllowed()).isFalse();
            assertThat(
                            current.checkAdminPermission(
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
                                    actor.userId(),
                                    actor.sessionId(),
                                    actor.sessionVersion(),
                                    actor.rolesVersion(),
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
            try (var ignored = identityFixture.unavailableAdminAuthority(authority)) {
                assertThatThrownBy(() -> current.checkAdminPermission(request))
                        .isInstanceOf(StatusRuntimeException.class)
                        .satisfies(
                                e ->
                                        assertThat(
                                                        ((StatusRuntimeException) e)
                                                                .getStatus()
                                                                .getCode())
                                                .isEqualTo(Status.Code.UNAVAILABLE));
            }
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
