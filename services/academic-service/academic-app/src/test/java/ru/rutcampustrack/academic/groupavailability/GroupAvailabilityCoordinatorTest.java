package ru.rutcampustrack.academic.groupavailability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authoperations.grpc.*;

import java.util.List;
import java.util.UUID;

class GroupAvailabilityCoordinatorTest {
    private final GroupAvailabilityStore store = mock(GroupAvailabilityStore.class);
    private final AuthCredentialReaderInvalidationClient auth =
            mock(AuthCredentialReaderInvalidationClient.class);
    private final IdentityCatalogSynchronizer catalog = mock(IdentityCatalogSynchronizer.class);
    private final GroupAvailabilityCoordinator coordinator =
            new GroupAvailabilityCoordinator(store, auth, catalog, mock(RequestContext.class));
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void stagedActorRevocationRetainsFailedSafeGuardsAndDoesNotPublish() {
        var prepared = operation(GroupAvailabilityStore.Phase.AUTH_INVALIDATED, true, false, null);
        var local = operation(GroupAvailabilityStore.Phase.LOCAL_COMMITTED, true, false, null);
        var failed =
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE,
                        true,
                        0);
        var terminal = operation(GroupAvailabilityStore.Phase.FAILED_SAFE, true, false, null);
        when(store.apply(prepared)).thenReturn(local);
        when(auth.commit(any())).thenReturn(failed);
        when(store.failed(local, failed)).thenReturn(terminal);
        assertThat(coordinator.execute(prepared)).isSameAs(terminal);
        verify(store, never()).succeeded(any(), any());
        verifyNoInteractions(catalog);
    }

    @Test
    void definiteLocalRollbackPersistsStopIntentThenRetriesAuthFailSafely() {
        var prepared = operation(GroupAvailabilityStore.Phase.AUTH_INVALIDATED, true, false, null);
        var stopped = operation(GroupAvailabilityStore.Phase.AUTH_INVALIDATED, true, true, null);
        var failed =
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_FAILED_SAFE,
                        true,
                        0);
        when(store.apply(prepared)).thenThrow(Status.ABORTED.asRuntimeException());
        when(store.stopApply(prepared)).thenReturn(stopped);
        when(auth.fail(any())).thenReturn(failed);
        var terminal = operation(GroupAvailabilityStore.Phase.FAILED_SAFE, true, true, null);
        when(store.failed(stopped, failed)).thenReturn(terminal);
        assertThat(coordinator.execute(prepared)).isSameAs(terminal);
        var order = inOrder(store, auth);
        order.verify(store).apply(prepared);
        order.verify(store).stopApply(prepared);
        order.verify(auth)
                .fail(
                        argThat(
                                r ->
                                        r.getExpectedOperationRevision() == 1
                                                && r.getCommandHash()
                                                        .equals(prepared.auth().getCommandHash())));
        order.verify(store).failed(stopped, failed);
        verify(auth, never()).commit(any());
    }

    @Test
    void unknownLocalCommitOutcomeNeverBecomesDefiniteRollback() {
        var prepared = operation(GroupAvailabilityStore.Phase.AUTH_INVALIDATED, true, false, null);
        var unknown = new DataAccessResourceFailureException("commit outcome unknown");
        when(store.apply(prepared)).thenThrow(unknown);
        assertThatThrownBy(() -> coordinator.execute(prepared)).isSameAs(unknown);
        verify(store, never()).stopApply(any());
        verify(auth, never()).fail(any());
        verify(auth, never()).commit(any());
    }

    @Test
    void verifiedEmptyHeadmanSetCanPublishAtInitialZeroCatalogVersion() {
        var local = operation(GroupAvailabilityStore.Phase.LOCAL_COMMITTED, false, false, null);
        var committed =
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED,
                        false,
                        0);
        var terminal = operation(GroupAvailabilityStore.Phase.SUCCEEDED, false, false, null);
        when(auth.commit(any())).thenReturn(committed);
        when(store.succeeded(local, committed)).thenReturn(terminal);
        assertThat(coordinator.execute(local)).isSameAs(terminal);
        verify(catalog).await(eq(0L), anyLong());
    }

    @Test
    void delayedPositivePrepareWinsNegativeRequestAndItsRealOperationContinues() {
        var accepted =
                operation(
                        GroupAvailabilityStore.Phase.ACCEPTED,
                        true,
                        false,
                        IdentityOperationRejectionReason
                                .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT);
        var prepared = operation(GroupAvailabilityStore.Phase.AUTH_INVALIDATED, true, false, null);
        var local = operation(GroupAvailabilityStore.Phase.LOCAL_COMMITTED, true, false, null);
        var positive =
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED,
                        true,
                        0);
        var committed =
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_COMMITTED,
                        true,
                        7);
        var terminal = operation(GroupAvailabilityStore.Phase.SUCCEEDED, true, false, null);
        when(auth.reject(any())).thenReturn(positive);
        when(store.invalidated(accepted, positive)).thenReturn(prepared);
        when(store.apply(prepared)).thenReturn(local);
        when(auth.commit(any())).thenReturn(committed);
        when(store.succeeded(local, committed)).thenReturn(terminal);
        assertThat(coordinator.execute(accepted)).isSameAs(terminal);
        verify(store, never()).rejected(any(), any());
    }

    @Test
    void missingAuthOperationRetainsUnknownAcceptanceWithoutTerminalOrNewActor() {
        var accepted = operation(GroupAvailabilityStore.Phase.ACCEPTED, true, false, null);
        when(store.find(ID)).thenReturn(accepted);
        when(auth.get(any())).thenThrow(Status.NOT_FOUND.asRuntimeException());
        assertThatThrownBy(() -> coordinator.recover(ID))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
        verify(auth, never()).reject(any());
        verify(auth, never()).prepare(any());
        verify(store, never()).rejected(any(), any());
    }

    @Test
    void changedHeadmanReceiptCannotCommitOrReleaseScope() {
        var local = operation(GroupAvailabilityStore.Phase.LOCAL_COMMITTED, true, false, null);
        var changed =
                local.receipts().toBuilder()
                        .setHeadmen(
                                0,
                                local.receipts().getHeadmen(0).toBuilder()
                                        .setAppliedAuthRolesVersion(6))
                        .build();
        var malformed =
                new GroupAvailabilityStore.Operation(
                        local.id(),
                        local.phase(),
                        local.command(),
                        local.auth(),
                        local.result(),
                        changed,
                        local.actorId(),
                        local.actor(),
                        local.supersedes(),
                        local.rejection(),
                        local.localRejected(),
                        local.apiIntent());
        assertThatThrownBy(() -> coordinator.execute(malformed))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e ->
                                assertThat(e.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        verify(auth, never()).commit(any());
        verify(store, never()).succeeded(any(), any());
    }

    private static GroupAvailabilityStore.Operation operation(
            GroupAvailabilityStore.Phase phase,
            boolean headman,
            boolean stopped,
            IdentityOperationRejectionReason rejection) {
        var command =
                new GroupAvailabilityCommand(
                        GroupAvailabilityCommand.Action.RESTORE,
                        7,
                        "0".repeat(64),
                        null,
                        null,
                        null,
                        List.of(new GroupAvailabilityCommand.GroupRevision(7, 1)));
        var group = CredentialReaderGroup.newBuilder().setGroupId(7).setExpectedGroupRevision(1);
        if (headman)
            group.setHeadmanUserId(8)
                    .setExpectedHeadmanRolesVersion(4)
                    .setExpectedHeadmanAcademicRevision(2);
        var auth =
                PrepareCredentialReaderInvalidationRequest.newBuilder()
                        .setOperationId(ID.toString())
                        .setAction(
                                CredentialReaderInvalidationAction
                                        .CREDENTIAL_READER_INVALIDATION_ACTION_GROUP_RESTORE)
                        .setCommandHash(ByteString.copyFrom(new byte[32]))
                        .setAcademicCommandHash(ByteString.copyFrom(new byte[32]))
                        .addGroups(group)
                        .build();
        var receipt =
                CommitCredentialReaderInvalidationRequest.newBuilder()
                        .addGroups(
                                CredentialReaderGroupCommitReceipt.newBuilder()
                                        .setGroupId(7)
                                        .setPreviousGroupRevision(1)
                                        .setCommittedGroupRevision(2));
        if (headman)
            receipt.addHeadmen(
                    AcademicIdentityCommitReceipt.newBuilder()
                            .setUserId(8)
                            .setPreviousAcademicRevision(2)
                            .setCommittedAcademicRevision(3)
                            .setAppliedAuthRolesVersion(5));
        return new GroupAvailabilityStore.Operation(
                ID,
                phase,
                command,
                auth,
                result(
                        CredentialReaderInvalidationPhase
                                .CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED,
                        headman,
                        0),
                phase == GroupAvailabilityStore.Phase.LOCAL_COMMITTED ? receipt.build() : null,
                99L,
                IdentityOperationActor.newBuilder()
                        .setUserId(99)
                        .setSessionId(ID.toString())
                        .setSessionVersion(1)
                        .setRolesVersion(4)
                        .build(),
                null,
                rejection,
                stopped,
                GroupAvailabilityIntent.direct(
                        command.action(), command.scopeGroupId(), command.requestDigest()));
    }

    private static CredentialReaderInvalidationResult result(
            CredentialReaderInvalidationPhase phase, boolean headman, long catalog) {
        var result =
                CredentialReaderInvalidationResult.newBuilder()
                        .setOperationId(ID.toString())
                        .setCommandHash(ByteString.copyFrom(new byte[32]))
                        .setPhase(phase)
                        .setOperationRevision(
                                phase
                                                == CredentialReaderInvalidationPhase
                                                        .CREDENTIAL_READER_INVALIDATION_PHASE_INVALIDATED
                                        ? 1
                                        : 2)
                        .setCommittedCatalogVersion(catalog);
        if (headman)
            result.addHeadmen(
                    PreparedIdentityTarget.newBuilder()
                            .setUserId(8)
                            .setExpectedAcademicRevision(2)
                            .setFinalRolesVersion(5));
        return result.build();
    }
}
