package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authoperations.grpc.*;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

class AcademicIdentityArchiveRollbackTest {
    @Test
    void changedAcceptedArchivePreviewFailsSafeAfterLocalRollbackAndRecoveryClosesAuth()
            throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(
                        startsWith("SELECT academic_revision FROM groups"), eq(Long.class), eq(7L)))
                .thenReturn(List.of(1L));
        when(jdbc.queryForList(
                        startsWith("SELECT id FROM academic_profiles"), eq(Long.class), eq(8L)))
                .thenReturn(List.of(8L));
        var repository = spy(new UserArchiveRepository(jdbc));
        doReturn(
                        new UserArchiveRepository.Local(
                                8, "c".repeat(64), "active", 1, 0, 0, false, Instant.now()))
                .when(repository)
                .observe(8);
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("ARCHIVE", 8, "0".repeat(64)),
                        null,
                        LocalDate.of(2026, 4, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "archive",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)),
                        new AcademicIdentityLocalCommand.ArchiveAdmission(
                                "b".repeat(64), "a".repeat(64), false));
        var rejected =
                assertThrows(
                        StatusRuntimeException.class,
                        () -> repository.verifyAcceptedArchive(8, local));
        assertThat(rejected.getStatus().getCode()).isEqualTo(Status.Code.ABORTED);
        var fixture = new Recovery();
        when(fixture.store.apply(fixture.prepared)).thenThrow(rejected);
        when(fixture.store.rejectedBeforeCommit(fixture.prepared)).thenReturn(fixture.localFailed);
        when(fixture.auth.fail(any())).thenReturn(fixture.authFailed);
        when(fixture.store.failedSafe(fixture.localFailed, fixture.authFailed))
                .thenReturn(fixture.completedFailed);
        assertThat(fixture.coordinator.recover(fixture.command.getOperationId()))
                .isSameAs(fixture.completedFailed);
        var order = inOrder(fixture.store, fixture.auth);
        order.verify(fixture.store).rejectedBeforeCommit(fixture.prepared);
        order.verify(fixture.auth)
                .fail(
                        argThat(
                                request ->
                                        request.getErrorCode().equals("ACADEMIC_CONFLICT")
                                                && request.getExpectedOperationRevision() == 1));
        order.verify(fixture.store).failedSafe(fixture.localFailed, fixture.authFailed);
        verify(fixture.auth, never()).commit(any());
    }

    @Test
    void unknownLocalCommitOutcomeDoesNotBecomeDefiniteRollbackOrAuthFailure() throws Exception {
        var fixture = new Recovery();
        var unknown = new DataAccessResourceFailureException("commit outcome unknown");
        when(fixture.store.apply(fixture.prepared)).thenThrow(unknown);
        assertThatThrownBy(() -> fixture.coordinator.recover(fixture.command.getOperationId()))
                .isSameAs(unknown);
        verify(fixture.store, never()).rejectedBeforeCommit(any());
        verify(fixture.auth, never()).fail(any());
        verify(fixture.auth, never()).commit(any());
    }

    private static final class Recovery {
        final AcademicIdentityCoordinator.Store store =
                mock(AcademicIdentityCoordinator.Store.class);
        final AcademicIdentityCoordinator.Auth auth = mock(AcademicIdentityCoordinator.Auth.class);
        final AcademicIdentityCoordinator coordinator =
                new AcademicIdentityCoordinator(
                        store, auth, mock(IdentityCatalogSynchronizer.class));
        final PrepareIdentityOperationRequest command;
        final IdentityOperationResult authPrepared;
        final IdentityOperationResult authFailed;
        final AcademicIdentityCoordinator.Operation prepared;
        final AcademicIdentityCoordinator.Operation localFailed;
        final AcademicIdentityCoordinator.Operation completedFailed;

        Recovery() throws Exception {
            var request =
                    PrepareIdentityOperationRequest.newBuilder()
                            .setOperationId("00000000-0000-0000-0000-000000000001")
                            .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE)
                            .addTargets(
                                    IdentityTargetCommand.newBuilder()
                                            .setUserId(8)
                                            .setExpectedAcademicRevision(2)
                                            .setExpectedRolesVersion(4));
            request.setCommandHash(
                    ByteString.copyFrom(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(request.build().toByteArray())));
            command = request.build();
            authPrepared =
                    IdentityOperationResult.newBuilder()
                            .setOperationId(command.getOperationId())
                            .setCommandHash(command.getCommandHash())
                            .setKind(command.getKind())
                            .setPhase(
                                    IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED)
                            .setOperationRevision(1)
                            .addTargets(
                                    PreparedIdentityTarget.newBuilder()
                                            .setUserId(8)
                                            .setExpectedAcademicRevision(2)
                                            .setFinalRolesVersion(5)
                                            .setCredentialsVersion(1)
                                            .setTelegramBindingVersion(1))
                            .build();
            authFailed =
                    authPrepared.toBuilder()
                            .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_FAILED_SAFE)
                            .setOperationRevision(2)
                            .build();
            prepared = operation(AcademicIdentityCoordinator.Phase.AUTH_PREPARED, authPrepared);
            localFailed = operation(AcademicIdentityCoordinator.Phase.FAILED_SAFE, authPrepared);
            completedFailed = operation(AcademicIdentityCoordinator.Phase.FAILED_SAFE, authFailed);
            when(store.find(command.getOperationId())).thenReturn(prepared);
        }

        private AcademicIdentityCoordinator.Operation operation(
                AcademicIdentityCoordinator.Phase phase, IdentityOperationResult result) {
            return new AcademicIdentityCoordinator.Operation(
                    command.getOperationId(), phase, command, result, List.of());
        }
    }
}
