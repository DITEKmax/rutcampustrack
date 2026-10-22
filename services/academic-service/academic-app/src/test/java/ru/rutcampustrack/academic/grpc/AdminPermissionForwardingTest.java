package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.authauthority.grpc.AuthorityAdminProfile;
import ru.rutcampustrack.authauthority.grpc.CheckAdminAccessResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Tests forwarding from an already validated gRPC context; real TLS/Auth admission is separate. */
class AdminPermissionForwardingTest {
    private static final String ORIGINAL_TOKEN = "original-signed-actor";
    private final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    private final AcademicGrpcServiceImpl service =
            mock(AcademicGrpcServiceImpl.class, CALLS_REAL_METHODS);
    private final InternalJwtClaims actor =
            new InternalJwtClaims(
                    99, UUID.randomUUID(), 1, 4, "ADMIN", "ACTIVE", null, false, false);

    AdminPermissionForwardingTest() {
        ReflectionTestUtils.setField(service, "adminIdentityData", auth);
    }

    @Test
    void originalTokenAndRequestedCapabilityReachFreshAuthWithoutServletContext() {
        when(auth.checkAdminAccessForSignedGrpcActor(
                        eq(ORIGINAL_TOKEN), eq(List.of("USERS_VIEW")), eq(false), anyLong()))
                .thenReturn(profile(99, "LIMITED"));
        var response =
                call(AdminPermissionCheckRequest.newBuilder().addPermissions("USERS_VIEW").build());
        assertThat(response.error.get()).isNull();
        assertThat(response.value.get().getAllowed()).isTrue();
        verify(auth)
                .checkAdminAccessForSignedGrpcActor(
                        eq(ORIGINAL_TOKEN), eq(List.of("USERS_VIEW")), eq(false), anyLong());
        assertThat(StudentHomeworkGrpcIdentity.ADMIN_PERMISSION_TOKEN.get()).isNull();
    }

    @Test
    void fullOnlyIgnoresPermissionListButOrdinaryEmptyAndUnknownNeverGrant() {
        when(auth.checkAdminAccessForSignedGrpcActor(
                        eq(ORIGINAL_TOKEN), eq(List.of()), anyBoolean(), anyLong()))
                .thenReturn(profile(99, "FULL"));
        assertThat(
                        call(AdminPermissionCheckRequest.newBuilder()
                                        .setFullOnly(true)
                                        .addPermissions("UNKNOWN")
                                        .build())
                                .value
                                .get()
                                .getAllowed())
                .isTrue();
        assertThat(call(AdminPermissionCheckRequest.getDefaultInstance()).value.get().getAllowed())
                .isFalse();
        assertThat(
                        call(AdminPermissionCheckRequest.newBuilder()
                                        .addPermissions("UNKNOWN")
                                        .build())
                                .value
                                .get()
                                .getAllowed())
                .isFalse();
        verify(auth, times(3))
                .checkAdminAccessForSignedGrpcActor(
                        eq(ORIGINAL_TOKEN), eq(List.of()), anyBoolean(), anyLong());
    }

    @Test
    void changedActorTupleCannotUseSuccessfulAuthResponseForAnotherIdentity() {
        when(auth.checkAdminAccessForSignedGrpcActor(
                        anyString(), anyCollection(), anyBoolean(), anyLong()))
                .thenReturn(profile(100, "FULL"));
        var result = call(AdminPermissionCheckRequest.newBuilder().setFullOnly(true).build());
        assertThat(result.value.get()).isNull();
        assertThat(Status.fromThrowable(result.error.get()).getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void currentRevocationDeniesAndUnavailableNeverLooksLikeFullOrNormalDeny() {
        when(auth.checkAdminAccessForSignedGrpcActor(
                        anyString(), anyCollection(), anyBoolean(), anyLong()))
                .thenThrow(
                        Status.PERMISSION_DENIED.asRuntimeException(),
                        Status.UNAVAILABLE.asRuntimeException());
        var request = AdminPermissionCheckRequest.newBuilder().addPermissions("USERS_VIEW").build();
        var denied = call(request);
        assertThat(denied.error.get()).isNull();
        assertThat(denied.value.get().getAllowed()).isFalse();
        var unknown = call(request);
        assertThat(unknown.value.get()).isNull();
        assertThat(Status.fromThrowable(unknown.error.get()).getCode())
                .isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    void claimsWithoutOriginalSignedTokenCannotMintOrForwardNewActor() {
        var observer = new Result();
        Context.current()
                .withValue(StudentHomeworkGrpcIdentity.CLAIMS, actor)
                .run(
                        () ->
                                service.checkAdminPermission(
                                        AdminPermissionCheckRequest.newBuilder()
                                                .setFullOnly(true)
                                                .build(),
                                        observer));
        assertThat(observer.value.get()).isNull();
        assertThat(Status.fromThrowable(observer.error.get()).getCode())
                .isEqualTo(Status.Code.UNAUTHENTICATED);
        verifyNoInteractions(auth);
    }

    private Result call(AdminPermissionCheckRequest request) {
        var observer = new Result();
        Context.current()
                .withValue(StudentHomeworkGrpcIdentity.CLAIMS, actor)
                .withValue(StudentHomeworkGrpcIdentity.ADMIN_PERMISSION_TOKEN, ORIGINAL_TOKEN)
                .run(() -> service.checkAdminPermission(request, observer));
        return observer;
    }

    private static CheckAdminAccessResponse profile(long user, String mode) {
        var profile =
                AuthorityAdminProfile.newBuilder().setAssigned(true).setMode(mode).setVersion(1);
        if (mode.equals("LIMITED")) profile.addPermissions("USERS_VIEW");
        return CheckAdminAccessResponse.newBuilder()
                .setActorUserId(user)
                .setSessionVersion(1)
                .setRolesVersion(4)
                .setProfile(profile)
                .build();
    }

    private static final class Result implements StreamObserver<AdminPermissionCheckResponse> {
        final AtomicReference<AdminPermissionCheckResponse> value = new AtomicReference<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onNext(AdminPermissionCheckResponse response) {
            value.set(response);
        }

        @Override
        public void onError(Throwable failure) {
            error.set(failure);
        }

        @Override
        public void onCompleted() {}
    }
}
