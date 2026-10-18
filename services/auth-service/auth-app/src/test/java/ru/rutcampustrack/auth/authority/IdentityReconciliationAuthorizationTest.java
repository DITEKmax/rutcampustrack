package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.util.List;

class IdentityReconciliationAuthorizationTest {
    @Test
    void usersManageCannotRecoverAnArchiveWithoutCurrentArchivePermission() {
        var manager = profile("USERS_VIEW", "USERS_MANAGE");
        assertThatThrownBy(
                        () ->
                                AuthIdentityOperationStore.requirePermission(
                                        manager,
                                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void rightfulArchiveAndHeadmanRecoveryUseTheirOriginalPermission() {
        assertThatCode(
                        () ->
                                AuthIdentityOperationStore.requirePermission(
                                        profile("USERS_ARCHIVE"),
                                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                AuthIdentityOperationStore.requirePermission(
                                        profile("GROUPS_MANAGE"),
                                        IdentityOperationKind
                                                .IDENTITY_OPERATION_KIND_HEADMAN_REPLACE))
                .doesNotThrowAnyException();
    }

    @Test
    void aFailedRoleIntentCannotBecomeArchiveOrActivateAnotherGrant() {
        var before = target();
        var archive =
                before.toBuilder()
                        .setDesiredAccountState(
                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ARCHIVED)
                        .setDesiredGrants(
                                0, before.getDesiredGrants(0).toBuilder().setStatus("ARCHIVED"))
                        .build();
        assertChanged(before, archive);
        assertChanged(
                before,
                before.toBuilder()
                        .addDesiredGrants(
                                AdmissionGrant.newBuilder().setRole("TEACHER").setStatus("ACTIVE"))
                        .build());
    }

    @Test
    void recoveryMayRefreshGenerationsButCannotChangeBindingOrAdminIntent() {
        var before = target();
        var fresh =
                before.toBuilder()
                        .setExpectedRolesVersion(13)
                        .setExpectedCredentialsVersion(9)
                        .setExpectedTelegramBindingVersion(8)
                        .setExpectedAcademicRevision(14)
                        .setSupersedesFailedOperationId("older-operation")
                        .setDesiredAdminProfile(
                                before.getDesiredAdminProfile().toBuilder().setExpectedVersion(7))
                        .setDesiredGrants(0, before.getDesiredGrants(0).toBuilder().setGrantId(42))
                        .build();
        assertThatCode(
                        () ->
                                AuthIdentityOperationStore.requireSameReconciliationIntent(
                                        before, fresh))
                .doesNotThrowAnyException();
        assertChanged(
                before,
                fresh.toBuilder()
                        .setTelegramChange(TelegramBindingChange.newBuilder().setTelegramId(1234))
                        .build());
        assertChanged(
                before,
                fresh.toBuilder()
                        .setDesiredAdminProfile(
                                AdmissionAdminProfile.newBuilder()
                                        .setAssigned(true)
                                        .setMode("FULL"))
                        .build());
    }

    @Test
    void combinedHeadmanAndTelegramChangeRequiresBothCurrentPermissionsIncludingRecovery() {
        var command =
                PrepareIdentityOperationRequest.newBuilder()
                        .addTargets(
                                target().toBuilder()
                                        .setTelegramChange(
                                                TelegramBindingChange.newBuilder()
                                                        .setTelegramId(1234)))
                        .build();
        for (var profile : List.of(profile("GROUPS_MANAGE"), profile("USERS_MANAGE")))
            assertThatThrownBy(
                            () ->
                                    AuthIdentityOperationStore.requireCommandPermission(
                                            profile,
                                            IdentityOperationKind
                                                    .IDENTITY_OPERATION_KIND_HEADMAN_REPLACE,
                                            command))
                    .isInstanceOf(StatusRuntimeException.class);
        assertThatCode(
                        () ->
                                AuthIdentityOperationStore.requireCommandPermission(
                                        profile("USERS_MANAGE", "GROUPS_MANAGE"),
                                        IdentityOperationKind
                                                .IDENTITY_OPERATION_KIND_HEADMAN_REPLACE,
                                        command))
                .doesNotThrowAnyException();
    }

    private static void assertChanged(IdentityTargetCommand before, IdentityTargetCommand next) {
        assertThatThrownBy(
                        () ->
                                AuthIdentityOperationStore.requireSameReconciliationIntent(
                                        before, next))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(
                        f ->
                                org.assertj.core.api.Assertions.assertThat(
                                                ((StatusRuntimeException) f)
                                                        .getTrailers()
                                                        .get(IdentityOperationFailure.CODE))
                                        .isEqualTo("RECONCILIATION_INTENT_CHANGED"));
    }

    private static IdentityTargetCommand target() {
        return IdentityTargetCommand.newBuilder()
                .setUserId(10)
                .setExpectedRolesVersion(2)
                .setExpectedCredentialsVersion(1)
                .setExpectedTelegramBindingVersion(1)
                .setDesiredAccountState(IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                .setDesiredAdminProfile(AdmissionAdminProfile.getDefaultInstance())
                .addDesiredGrants(
                        AdmissionGrant.newBuilder()
                                .setGrantId(20)
                                .setRole("STUDENT")
                                .setStatus("ACTIVE")
                                .setGroupId(30))
                .build();
    }

    private static AdminAccessProfile profile(String... permissions) {
        return new AdminAccessProfile(1, true, "LIMITED", List.of(permissions), 1);
    }
}
