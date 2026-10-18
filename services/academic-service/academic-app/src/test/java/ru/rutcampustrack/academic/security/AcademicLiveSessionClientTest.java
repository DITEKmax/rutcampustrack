package ru.rutcampustrack.academic.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.authauthority.grpc.AuthorityAdminProfile;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminPermission;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

class AcademicLiveSessionClientTest {
    private final InternalJwtClaims actor =
            new InternalJwtClaims(
                    8, UUID.randomUUID(), 2, 3, "ADMIN", "ACTIVE", null, false, false);

    @Test
    void canonicalFullWireProfileAdmitsEveryEffectivePermission() {
        var profile = client(profile("FULL")).admit(actor);
        assertThat(profile.userId()).isEqualTo(actor.userId());
        assertThat(profile.assigned()).isTrue();
        assertThat(profile.mode()).isEqualTo("FULL");
        assertThat(profile.version()).isEqualTo(4);
        assertThat(profile.permissions()).containsExactlyElementsOf(AdminPermission.allCodes());
        assertThat(profile.permissions()).hasSize(12);
    }

    @Test
    void limitedAdmissionRetainsOnlyItsExplicitPermissions() {
        var wire = profile("LIMITED").toBuilder().addPermissions("USERS_VIEW").build();
        var admitted = client(wire).admit(actor);
        assertThat(admitted.full()).isFalse();
        assertThat(admitted.permissions()).containsExactly("USERS_VIEW");
        assertThat(admitted.allows("USERS_MANAGE")).isFalse();
        assertThat(admitted.version()).isEqualTo(4);
    }

    @Test
    void absentAssignmentStaysUnassignedAndMalformedProfilesFailClosed() {
        var absent = client(AuthorityAdminProfile.getDefaultInstance()).admit(actor);
        assertThat(absent.assigned()).isFalse();
        assertThat(absent.mode()).isNull();
        assertThat(absent.permissions()).isEmpty();
        for (var malformed :
                List.of(
                        profile("UNKNOWN"),
                        profile("FULL").toBuilder().addPermissions("USERS_VIEW").build(),
                        profile("FULL").toBuilder().setVersion(0).build(),
                        profile("LIMITED"),
                        profile("LIMITED").toBuilder().addPermissions("UNKNOWN").build(),
                        profile("LIMITED").toBuilder()
                                .addPermissions("ADMIN_ACCESS_MANAGE")
                                .build(),
                        profile("FULL").toBuilder().setAssigned(false).build(),
                        AuthorityAdminProfile.newBuilder().setVersion(-1).build())) {
            assertThatThrownBy(() -> client(malformed).admit(actor))
                    .isInstanceOfSatisfying(
                            StatusRuntimeException.class,
                            error ->
                                    assertThat(error.getStatus().getCode())
                                            .isEqualTo(Status.Code.UNAVAILABLE));
        }
    }

    @Test
    void revokedSessionAndMissingActorCannotBecomeAnAdminProfile() {
        var stub =
                mock(
                        AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub
                                .class,
                        RETURNS_SELF);
        when(stub.admitSessionIdentity(any()))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        var client = client(stub);
        assertThatThrownBy(() -> client.admit(actor))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
        assertThatThrownBy(() -> client.admit(null))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
    }

    private AuthorityAdminProfile profile(String mode) {
        return AuthorityAdminProfile.newBuilder()
                .setAssigned(true)
                .setMode(mode)
                .setVersion(4)
                .build();
    }

    private AcademicLiveSessionClient client(AuthorityAdminProfile profile) {
        var stub =
                mock(
                        AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub
                                .class,
                        RETURNS_SELF);
        when(stub.admitSessionIdentity(any()))
                .thenAnswer(
                        call ->
                                LiveSessionAuthority.newBuilder()
                                        .setIdentity((StoredSessionIdentity) call.getArgument(0))
                                        .setInternalToken("synthetic-admitted-token")
                                        .setAdminProfile(profile)
                                        .build());
        return client(stub);
    }

    private AcademicLiveSessionClient client(
            AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub stub) {
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var client = new AcademicLiveSessionClient(token, "auth-service");
        ReflectionTestUtils.setField(client, "stub", stub);
        return client;
    }
}
