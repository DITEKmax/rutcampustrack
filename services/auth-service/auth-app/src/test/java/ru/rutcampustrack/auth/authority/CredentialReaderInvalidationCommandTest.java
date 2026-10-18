package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.*;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.authoperations.grpc.*;

import java.util.UUID;

class CredentialReaderInvalidationCommandTest {
    @Test
    void emptyHeadmanIsExplicitAndDoesNotInventAReader() {
        var request = signed(base().addGroups(group(1, 0, 0, 0)));
        assertThat(CredentialReaderInvalidationCommand.validate(request)).isNotEmpty();
        assertThatThrownBy(
                        () ->
                                CredentialReaderInvalidationCommand.validate(
                                        signed(
                                                request.toBuilder()
                                                        .setGroups(0, group(1, 0, 1, 0)))))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
    }

    @Test
    void fixedAllGroupCycleHasNoInheritedTwoHundredLimit() {
        var request =
                base().setAction(
                                CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_MANUAL_CYCLE_ARCHIVE)
                        .setCycleSemesterId(9)
                        .setCycleYear(2026);
        for (int i = 1; i <= 201; i++) request.addGroups(group(i, i, 1, 1));
        assertThat(CredentialReaderInvalidationCommand.validate(signed(request))).isNotEmpty();
    }

    @Test
    void reconcileRefreshesVersionsButCannotChangeGroupsReaderActionOrDomainIntent() {
        var original = signed(base().addGroups(group(1, 7, 1, 2)));
        var retry =
                signed(
                        original.toBuilder()
                                .setOperationId(UUID.randomUUID().toString())
                                .setSupersedesFailedOperationId(original.getOperationId())
                                .setGroups(0, group(1, 7, 3, 4)));
        assertThat(CredentialReaderInvalidationCommand.intent(retry))
                .isEqualTo(CredentialReaderInvalidationCommand.intent(original));
        for (var changed :
                java.util.List.of(
                        retry.toBuilder().setGroups(0, group(1, 8, 3, 4)),
                        retry.toBuilder()
                                .setAction(
                                        CredentialReaderInvalidationAction
                                                .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_RESTORE),
                        retry.toBuilder()
                                .setAcademicCommandHash(
                                        ByteString.copyFrom(new byte[32])
                                                .substring(0, 31)
                                                .concat(ByteString.copyFrom(new byte[] {1})))))
            assertThat(CredentialReaderInvalidationCommand.intent(changed.build()))
                    .isNotEqualTo(CredentialReaderInvalidationCommand.intent(original));
    }

    @Test
    void duplicateReaderOrUnorderedGroupManifestCannotHideScope() {
        var cycle =
                base().setAction(
                                CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE)
                        .setCycleSemesterId(9)
                        .setCycleYear(2026);
        assertThatThrownBy(
                        () ->
                                CredentialReaderInvalidationCommand.validate(
                                        signed(
                                                cycle.clone()
                                                        .addGroups(group(1, 7, 1, 1))
                                                        .addGroups(group(2, 7, 1, 1)))))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
        assertThatThrownBy(
                        () ->
                                CredentialReaderInvalidationCommand.validate(
                                        signed(
                                                cycle.clone()
                                                        .addGroups(group(2, 7, 1, 1))
                                                        .addGroups(group(1, 8, 1, 1)))))
                .isInstanceOf(io.grpc.StatusRuntimeException.class);
    }

    private static PrepareCredentialReaderInvalidationRequest.Builder base() {
        return PrepareCredentialReaderInvalidationRequest.newBuilder()
                .setOperationId(UUID.randomUUID().toString())
                .setAcademicCommandHash(ByteString.copyFrom(new byte[32]))
                .setAction(
                        CredentialReaderInvalidationAction
                                .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_DEACTIVATE);
    }

    private static CredentialReaderGroup group(long group, long headman, long rv, long revision) {
        return CredentialReaderGroup.newBuilder()
                .setGroupId(group)
                .setExpectedGroupRevision(1)
                .setHeadmanUserId(headman)
                .setExpectedHeadmanRolesVersion(rv)
                .setExpectedHeadmanAcademicRevision(revision)
                .build();
    }

    private static PrepareCredentialReaderInvalidationRequest signed(
            PrepareCredentialReaderInvalidationRequest.Builder builder) {
        builder.clearCommandHash();
        return builder.setCommandHash(
                        ByteString.copyFrom(
                                CredentialReaderInvalidationCommand.sha(
                                        builder.build().toByteArray())))
                .build();
    }
}
