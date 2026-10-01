package ru.rutcampustrack.academic.integration;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.academic.semester.*;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionCounts;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.academic.event.HomeworkBindingArchivedEventConsumer;
import ru.rutcampustrack.academic.event.SemesterArchiveParticipantAcknowledgementConsumer;
import ru.rutcampustrack.schedule.grpc.SetSemesterArchiveBarrierResponse;
import ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState;
import org.springframework.dao.DataAccessException;
import ru.rutcampustrack.academic.homework.HomeworkBindingTransferCoordinator;
import ru.rutcampustrack.academic.homework.LessonTransferBatch;
import java.util.HashMap;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.grpc.SemesterStateRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.semester.SemesterService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL outcome checks and the real uncached gRPC handler's state/status mapping.
 * Calls the handler directly: authenticated TLS/service transport admission is outside
 * this fixture and stays covered by the dedicated service-identity transport tests.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.server.in-process-name=academic-grpc-test",
                "grpc.server.port=-1",
                "semester.archive.retry-delay-ms=3600000",
                "grpc.client.inProcess.address=in-process:academic-grpc-test",
                "grpc.client.inProcess.negotiationType=plaintext"
        }
)
class SemesterArchiveStateIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private AcademicGrpcServiceImpl grpcHandler;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SemesterService semesterService;

    @Autowired
    private SemesterRepository semesterRepository;

    @Autowired private SemesterArchiveCommandTransaction deletionCommands;
    @Autowired private AcademicSemesterArchiveBarrierTransaction deletionBarrier;
    @Autowired private AcademicSemesterDeletionSnapshotReader deletionSnapshots;
    @Autowired private SemesterArchiveCoordinator deletionCoordinator;
    @Autowired private HomeworkBindingArchivedEventConsumer archiveConsumer;
    @Autowired private SemesterArchiveParticipantAcknowledgementConsumer participantAckConsumer;
    @Autowired private HomeworkBindingTransferCoordinator transferCoordinator;
    @MockitoBean private ScheduleGrpcClient scheduleClient;
    @MockitoBean private SemesterDeletionPreviewService deletionPreviews;

    private static final String REMOTE_DIGEST = "b".repeat(64);
    private static final SemesterDeletionCounts ZERO_COUNTS = new SemesterDeletionCounts(0, 0, 0, 0, 0, 0, 0);

    private final List<Long> fixtureIds = new ArrayList<>();
    private final List<Long> fixtureSubjectIds = new ArrayList<>();
    private List<Long> priorActiveSemesterIds;
    private Long semesterId;

    @BeforeEach
    void createActiveFixture() {
        priorActiveSemesterIds = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, row) -> rs.getLong(1));
        semesterId = insertInactiveFixture("archive-state-" + UUID.randomUUID());
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
    }

    @AfterEach
    void restoreFixtureAndPriorActiveState() {
        if (priorActiveSemesterIds == null) {
            return;
        }
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long fixtureId : fixtureIds) {
            deleteEmptyFixtureThroughProtocol(fixtureId);
        }
        for (Long priorId : priorActiveSemesterIds) {
            jdbcTemplate.update("UPDATE semesters SET is_archived = false, archive_transition = 'NONE', "
                    + "is_active = true, state_version = state_version + 1 WHERE id = ?", priorId);
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (Long subjectId : fixtureSubjectIds) {
                jdbcTemplate.update("DELETE FROM subject_lesson_types WHERE subject_id = ?", subjectId);
                jdbcTemplate.update("DELETE FROM subjects WHERE id = ?", subjectId);
            }
        });
        fixtureSubjectIds.clear();
        fixtureIds.clear();
    }

    @Test
    void getSemesterState_reportsTransitionsReplayAndWriteBlock() {
        SemesterStateResponse initial = state(semesterId);
        assertThat(initial.getActive()).isTrue();
        assertThat(initial.getArchived()).isFalse();
        assertThat(initial.getTransition().name()).isEqualTo("NONE");
        assertThat(initial.getWriteBlocked()).isFalse();

        long outboxBaseline = latestOutboxId();
        Semester archiving = semesterService.beginArchiveTransition(semesterId);
        assertThat(archiving.isActive()).isFalse();
        assertThat(archiving.isArchived()).isFalse();
        assertThat(archiving.getArchiveTransition().name()).isEqualTo("ARCHIVING");
        assertThat(activeSemesterIds()).isEmpty();

        SemesterStateResponse archivingState = state(semesterId);
        assertThat(archivingState.getId()).isEqualTo(semesterId);
        assertThat(archivingState.getActive()).isFalse();
        assertThat(archivingState.getArchived()).isFalse();
        assertThat(archivingState.getTransition().name()).isEqualTo("ARCHIVING");
        assertThat(archivingState.getWriteBlocked()).isTrue();
        assertThat(archivingState.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);
        Semester repeatedArchiving = semesterService.beginArchiveTransition(semesterId);
        assertThat(repeatedArchiving.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);

        assertThatThrownBy(() -> semesterService.activateSemester(semesterId))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> semesterService.completeArchiveTransition(
                semesterId, archiving.getStateVersion() - 1))
                .isInstanceOf(ConflictException.class);

        Semester archived = semesterService.completeArchiveTransition(
                semesterId, archiving.getStateVersion());
        SemesterStateResponse archivedState = state(semesterId);
        assertThat(archivedState.getActive()).isFalse();
        assertThat(archivedState.getArchived()).isTrue();
        assertThat(archivedState.getTransition().name()).isEqualTo("NONE");
        assertThat(archivedState.getWriteBlocked()).isTrue();
        assertThat(archivedState.getStateVersion()).isEqualTo(archived.getStateVersion());
        assertThat(archived.getStateVersion()).isEqualTo(archiving.getStateVersion());
        assertThat(semesterService.completeArchiveTransition(semesterId, archiving.getStateVersion())
                .getStateVersion())
                .isEqualTo(archived.getStateVersion());
        assertThatThrownBy(() -> semesterService.activateSemester(semesterId))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> semesterService.completeArchiveTransition(semesterId, -1))
                .isInstanceOf(ConflictException.class);

        Semester archivedRow = semesterRepository.findById(semesterId).orElseThrow();
        assertThatThrownBy(() -> semesterService.updateSemester(semesterId,
                new UpdateSemesterRequest(archivedRow.getName(), archivedRow.getDateFrom(), archivedRow.getDateTo())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("архив");

        Semester restoring = semesterService.beginRestoreTransition(semesterId);
        SemesterStateResponse restoringState = state(semesterId);
        assertThat(restoring.isActive()).isFalse();
        assertThat(restoringState.getActive()).isFalse();
        assertThat(restoringState.getArchived()).isTrue();
        assertThat(restoringState.getTransition().name()).isEqualTo("RESTORING");
        assertThat(restoringState.getWriteBlocked()).isTrue();
        assertThat(semesterService.beginRestoreTransition(semesterId).getStateVersion())
                .isEqualTo(restoring.getStateVersion());

        assertThatThrownBy(() -> semesterService.completeRestoreTransition(
                semesterId, restoring.getStateVersion() - 1))
                .isInstanceOf(ConflictException.class);
        semesterService.completeRestoreTransition(semesterId, restoring.getStateVersion());
        SemesterStateResponse releasePendingState = state(semesterId);
        assertThat(releasePendingState.getReleasePending()).isTrue();
        assertThat(releasePendingState.getWriteBlocked()).isTrue();
        assertThatThrownBy(() -> semesterService.activateSemester(semesterId)).isInstanceOf(ConflictException.class);
        Semester restored = semesterService.completeRestoreRelease(semesterId, restoring.getStateVersion());
        SemesterStateResponse restoredState = state(semesterId);
        assertThat(restoredState.getReleasePending()).isFalse();
        assertThat(restored.isActive()).isFalse();
        assertThat(restored.isArchived()).isFalse();
        assertThat(restoredState.getActive()).isFalse();
        assertThat(restoredState.getArchived()).isFalse();
        assertThat(restoredState.getTransition().name()).isEqualTo("NONE");
        assertThat(restoredState.getWriteBlocked()).isFalse();
        assertThat(restored.getStateVersion()).isEqualTo(restoring.getStateVersion());
        assertThat(activeSemesterIds()).isEmpty();
        assertThat(semesterService.completeRestoreTransition(semesterId, restoring.getStateVersion())
                .getStateVersion())
                .isEqualTo(restored.getStateVersion());
        assertThatThrownBy(() -> semesterService.completeRestoreTransition(semesterId, -1))
                .isInstanceOf(ConflictException.class);

        Semester restoredRow = semesterRepository.findById(semesterId).orElseThrow();
        semesterService.updateSemester(semesterId,
                new UpdateSemesterRequest(restoredRow.getName(), restoredRow.getDateFrom(), restoredRow.getDateTo()));
        assertThat(semesterService.activateSemester(semesterId).isActive()).isTrue();
        assertThat(state(semesterId).getActive()).isTrue();
        assertThat(activeSemesterIds()).containsExactly(semesterId);

        StatusRuntimeException missing = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> state(Long.MAX_VALUE));
        assertThat(missing.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        StatusRuntimeException invalid = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> state(0L));
        assertThat(invalid.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
    }

    @Test
    void archiveAndActivation_areSerializedAndKeepAtMostOneActiveSemester() throws Exception {
        long activationTargetId = insertInactiveFixture("activation-race-" + UUID.randomUUID());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Semester> archive = pool.submit(() -> awaitStartThenRun(
                    ready, start, () -> semesterService.beginArchiveTransition(semesterId)));
            Future<Semester> activate = pool.submit(() -> awaitStartThenRun(
                    ready, start, () -> semesterService.activateSemester(activationTargetId)));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            long outboxBaseline = latestOutboxId();
            start.countDown();
            Semester archivedTarget = archive.get(20, TimeUnit.SECONDS);
            Semester activeTarget = activate.get(20, TimeUnit.SECONDS);

            assertThat(archivedTarget.isActive()).isFalse();
            assertThat(archivedTarget.getArchiveTransition().name()).isEqualTo("ARCHIVING");
            assertThat(activeTarget.isActive()).isTrue();
            assertThat(activeSemesterIds()).containsExactly(activationTargetId);
            assertThat(state(semesterId).getWriteBlocked()).isTrue();
            assertThat(state(activationTargetId).getActive()).isTrue();
            assertThat(archivedEventIdsAfter(outboxBaseline)).containsExactly(semesterId);

            assertThat(activeSemesterIds()).containsExactly(activationTargetId);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void deleteStartTimeoutAndStalePreviewRestoreExactPriorState() {
        stubScheduleDeleteParticipant();
        UUID timeoutId = UUID.randomUUID();
        var activePreview = deletionSnapshot(semesterId);
        var started = deletionCommands.startDelete(semesterId, adminId(), timeoutId, timeoutId, activePreview);
        assertThat(started.getDeletePhase()).isEqualTo(SemesterDeletionPhase.PREPARING);
        assertThat(state(semesterId).getTransitionOperationId()).isEqualTo(timeoutId.toString());
        assertThat(state(semesterId).getWriteBlocked()).isTrue();
        assertThat(deletionCommands.startDelete(semesterId, adminId(), timeoutId, timeoutId, activePreview)
                .getStateVersion()).isEqualTo(started.getStateVersion());
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE semesters SET deletion_phase = 'DELETING' WHERE id = ?",
                semesterId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> deletionCommands.startDelete(semesterId, adminId(), UUID.randomUUID(),
                UUID.randomUUID(), activePreview)).isInstanceOf(ConflictException.class);
        long prepareCommandsBefore = deletionCommandCount(timeoutId, SemesterArchiveParticipantCommand.PREPARE_DELETE);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId, started.getStateVersion(),
                SemesterArchiveParticipantCommand.PREPARE_DELETE, SemesterArchiveParticipantStatus.PENDING));
        assertThat(deletionCommandCount(timeoutId, SemesterArchiveParticipantCommand.PREPARE_DELETE))
                .isEqualTo(prepareCommandsBefore);
        assertThat(deletionCommands.find(timeoutId).getAcademic()).isEqualTo(SemesterArchiveParticipantStatus.PENDING);
        deletionCoordinator.advance(timeoutId); // Existing periodic retry drives pending receipts.
        var preparedForSeal = deletionCommands.find(timeoutId);
        assertThat(preparedForSeal.getOperationState()).isEqualTo(SemesterArchiveOperationState.PENDING);
        assertThat(preparedForSeal.isAcademicSealed()).isTrue();
        assertThat(preparedForSeal.isScheduleSealed()).isTrue();
        assertThat(preparedForSeal.getAttendance()).isEqualTo(SemesterArchiveParticipantStatus.PENDING);
        assertThat(preparedForSeal.isAttendanceSealed()).isFalse();
        assertThat(deletionCommandCount(timeoutId, SemesterArchiveParticipantCommand.SEAL_DELETE)).isEqualTo(1L);
        jdbcTemplate.update("UPDATE semester_archive_operations SET prepare_expires_at = now() - interval '1 second' "
                + "WHERE operation_id = ?", timeoutId);
        deletionCoordinator.advance(timeoutId);
        assertThat(deletionCommands.find(timeoutId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.RELEASING);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId, started.getStateVersion(),
                SemesterArchiveParticipantCommand.PREPARE_DELETE, SemesterArchiveParticipantStatus.READY));
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId, started.getStateVersion(),
                SemesterArchiveParticipantCommand.SEAL_DELETE, SemesterArchiveParticipantStatus.READY));
        var releasing = deletionCommands.find(timeoutId);
        assertThat(releasing.getDeletePhase()).isEqualTo(SemesterDeletionPhase.RELEASING);
        assertThat(releasing.getAttendance()).isEqualTo(SemesterArchiveParticipantStatus.RELEASE_PENDING);
        assertThat(releasing.isAttendanceSealed()).isFalse();
        assertThat(releasing.getCancelReason()).isEqualTo("PREPARE_EXPIRED");
        assertThatThrownBy(() -> participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId,
                started.getStateVersion() + 1, SemesterArchiveParticipantCommand.PREPARE_DELETE,
                SemesterArchiveParticipantStatus.READY))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId,
                started.getStateVersion(), SemesterArchiveParticipantCommand.COMMIT_DELETE,
                SemesterArchiveParticipantStatus.DELETED))).isInstanceOf(ConflictException.class);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(timeoutId, started.getStateVersion(),
                SemesterArchiveParticipantCommand.RELEASE_DELETE, SemesterArchiveParticipantStatus.RELEASED));
        assertThat(deletionCommands.find(timeoutId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.CANCELLED);
        assertThat(state(semesterId).getActive()).isTrue();
        assertThat(state(semesterId).getWriteBlocked()).isFalse();
        assertThat(state(semesterId).getStateVersion()).isEqualTo(activePreview.stateVersion() + 2);

        var archiving = semesterService.beginArchiveTransition(semesterId);
        semesterService.completeArchiveTransition(semesterId, archiving.getStateVersion());
        UUID staleId = UUID.randomUUID();
        var archivedPreview = deletionSnapshot(semesterId);
        var archivedStart = deletionCommands.startDelete(semesterId, adminId(), staleId, staleId, archivedPreview);
        assertThat(state(semesterId).getArchived()).isTrue();
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(staleId, archivedStart.getStateVersion(),
                SemesterArchiveParticipantCommand.PREPARE_DELETE, SemesterArchiveParticipantStatus.PENDING));
        deletionCoordinator.advance(staleId);
        var awaitingAttendanceSeal = deletionCommands.find(staleId);
        assertThat(awaitingAttendanceSeal.isAcademicSealed()).isTrue();
        assertThat(awaitingAttendanceSeal.isScheduleSealed()).isTrue();
        assertThat(awaitingAttendanceSeal.isAttendanceSealed()).isFalse();
        assertThat(awaitingAttendanceSeal.isIrreversibleIntent()).isFalse();
        assertThat(deletionCommandCount(staleId, SemesterArchiveParticipantCommand.SEAL_DELETE)).isEqualTo(1L);
        for (int retry = 0; retry < 2; retry++) {
            participantAckConsumer.onEvent(deletionAttendanceEnvelope(staleId, archivedStart.getStateVersion(),
                    SemesterArchiveParticipantCommand.SEAL_DELETE, SemesterArchiveParticipantStatus.PENDING));
        }
        assertThat(deletionCommandCount(staleId, SemesterArchiveParticipantCommand.SEAL_DELETE)).isEqualTo(1L);
        assertThat(deletionCommands.find(staleId).isAttendanceSealed()).isFalse();
        assertThat(deletionCommands.find(staleId).isIrreversibleIntent()).isFalse();
        deletionCoordinator.advance(staleId); // A pending seal still retries at the scheduled boundary.
        assertThat(deletionCommandCount(staleId, SemesterArchiveParticipantCommand.SEAL_DELETE)).isEqualTo(2L);
        assertThat(deletionCommandCount(staleId, SemesterArchiveParticipantCommand.PREPARE_DELETE)).isEqualTo(1L);
        when(deletionPreviews.underFence(semesterId, archivedPreview.semesterName(), archivedPreview.stateVersion(),
                archivedPreview.priorState())).thenReturn(new SemesterDeletionPreviewService.Snapshot(
                    semesterId, archivedPreview.semesterName(), archivedPreview.priorState(), archivedPreview.stateVersion(),
                    "c".repeat(64), ZERO_COUNTS, archivedPreview.academicDigest(), REMOTE_DIGEST, REMOTE_DIGEST, 0, 0));
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(staleId, archivedStart.getStateVersion(),
                SemesterArchiveParticipantCommand.SEAL_DELETE, SemesterArchiveParticipantStatus.READY));
        assertThat(deletionCommands.find(staleId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.RELEASING);
        releasedAttendance(staleId);
        deletionCoordinator.advance(staleId);
        assertThat(deletionCommands.find(staleId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.CANCELLED);
        assertThat(deletionCommands.find(staleId).getCancelReason()).isEqualTo("STALE_PREVIEW");
        assertThat(state(semesterId).getArchived()).isTrue();
        assertThat(state(semesterId).getActive()).isFalse();
        assertThat(state(semesterId).getStateVersion()).isEqualTo(archivedPreview.stateVersion() + 2);

        UUID receiptDriftId = UUID.randomUUID();
        var receiptDriftPreview = deletionSnapshot(semesterId);
        var receiptDriftStart = deletionCommands.startDelete(semesterId, adminId(), receiptDriftId,
                receiptDriftId, receiptDriftPreview);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId, receiptDriftStart.getStateVersion(),
                SemesterArchiveParticipantCommand.PREPARE_DELETE, SemesterArchiveParticipantStatus.PENDING));
        deletionCoordinator.advance(receiptDriftId);
        String changedDigest = "d".repeat(64);
        assertThatThrownBy(() -> participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId,
                receiptDriftStart.getStateVersion() + 1, SemesterArchiveParticipantCommand.SEAL_DELETE,
                SemesterArchiveParticipantStatus.PENDING, changedDigest, "ATTENDANCE_DELETE_PREVIEW_CHANGED", 4)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId,
                receiptDriftStart.getStateVersion(), SemesterArchiveParticipantCommand.SEAL_DELETE,
                SemesterArchiveParticipantStatus.PENDING, REMOTE_DIGEST, "ATTENDANCE_DELETE_PREVIEW_CHANGED", 4)))
                .isInstanceOf(ConflictException.class);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId, receiptDriftStart.getStateVersion(),
                SemesterArchiveParticipantCommand.SEAL_DELETE, SemesterArchiveParticipantStatus.PENDING,
                changedDigest, "ATTENDANCE_DELETE_DRAIN_PENDING", 4));
        assertThat(deletionCommands.find(receiptDriftId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.PREPARING);
        long sealCommandsBeforeDrift = deletionCommandCount(receiptDriftId, SemesterArchiveParticipantCommand.SEAL_DELETE);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId, receiptDriftStart.getStateVersion(),
                SemesterArchiveParticipantCommand.SEAL_DELETE, SemesterArchiveParticipantStatus.PENDING,
                changedDigest, "ATTENDANCE_DELETE_PREVIEW_CHANGED", 4));
        var releasingDrift = deletionCommands.find(receiptDriftId);
        assertThat(releasingDrift.getDeletePhase()).isEqualTo(SemesterDeletionPhase.RELEASING);
        assertThat(releasingDrift.getCancelReason()).isEqualTo("STALE_PREVIEW");
        assertThat(releasingDrift.isIrreversibleIntent()).isFalse();
        assertThat(releasingDrift.getPrepareExpiresAt()).isAfter(java.time.OffsetDateTime.now());
        assertThat(releasingDrift.getAttendanceParticipantDigest()).isEqualTo(REMOTE_DIGEST);
        assertThat(releasingDrift.getAttendanceMarksCount()).isZero();
        assertThat(releasingDrift.getPreviewDigest()).isEqualTo(receiptDriftPreview.digest());
        assertThat(releasingDrift.getAcademic()).isEqualTo(SemesterArchiveParticipantStatus.RELEASED);
        assertThat(releasingDrift.getSchedule()).isEqualTo(SemesterArchiveParticipantStatus.RELEASED);
        assertThat(deletionCommandCount(receiptDriftId, SemesterArchiveParticipantCommand.SEAL_DELETE))
                .isEqualTo(sealCommandsBeforeDrift);
        participantAckConsumer.onEvent(deletionAttendanceEnvelope(receiptDriftId, receiptDriftStart.getStateVersion(),
                SemesterArchiveParticipantCommand.RELEASE_DELETE, SemesterArchiveParticipantStatus.RELEASED,
                changedDigest, null, 4));
        assertThat(deletionCommands.find(receiptDriftId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.CANCELLED);
        assertThat(deletionCommands.find(receiptDriftId).getCancelReason()).isEqualTo("STALE_PREVIEW");
        assertThat(state(semesterId).getArchived()).isTrue();
        assertThat(state(semesterId).getStateVersion()).isEqualTo(receiptDriftPreview.stateVersion() + 2);
    }

    @Test
    void deletePreparingDrainsExactHomeworkEffectsAndRejectsOrdinaryWrites() {
        long actorId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        long groupId = jdbcTemplate.queryForObject("SELECT id FROM groups WHERE name = '\u0418\u0412\u0422-211'", Long.class);
        long subjectId = new TransactionTemplate(transactionManager).execute(status -> {
            long id = jdbcTemplate.queryForObject("INSERT INTO subjects (name, type, group_id) "
                    + "VALUES (?, 'lecture', ?) RETURNING id", Long.class,
                    "delete-drain-" + UUID.randomUUID(), groupId);
            jdbcTemplate.update("INSERT INTO subject_lesson_types (subject_id, lesson_type) VALUES (?, 'lecture')", id);
            return id;
        });
        fixtureSubjectIds.add(subjectId);
        long bindingId = System.currentTimeMillis() * 100L;
        UUID requestKey = UUID.randomUUID();
        byte[] hash = new byte[32];
        long homeworkId = jdbcTemplate.queryForObject("""
                INSERT INTO homeworks (group_id, subject_id, semester_id, lesson_date, lesson_number,
                    title, published_by, binding_id, actor_id, request_key, payload_hash, publication_state,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, 1, 'delete-drain', ?, ?, ?, ?, ?, 'PENDING', now(), now()) RETURNING id
                """, Long.class, groupId, subjectId, semesterId, LocalDate.of(9990, 1, 2),
                actorId, bindingId, actorId, requestKey, hash);
        UUID operationId = UUID.randomUUID();
        var started = deletionCommands.startDelete(semesterId, adminId(), operationId, operationId,
                deletionSnapshot(semesterId));
        assertThat(deletionBarrier.prepareDelete(operationId, semesterId, started.getStateVersion())).isNotNull();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE homeworks SET title = 'ordinary write' WHERE id = ?",
                homeworkId)).isInstanceOf(DataAccessException.class);
        UUID materializedEvent = UUID.randomUUID();
        archiveConsumer.onEvent(archiveEnvelope(materializedEvent, bindingId, actorId, requestKey, homeworkId));
        assertThat(jdbcTemplate.queryForObject("SELECT publication_state FROM homeworks WHERE id = ?",
                String.class, homeworkId)).isEqualTo("ARCHIVED");
        assertAppliedEffect(materializedEvent);

        long emptyBinding = bindingId + 10;
        UUID emptyRequest = UUID.randomUUID();
        UUID transferEvent = UUID.randomUUID();
        UUID transferOperation = UUID.randomUUID();
        var acceptedTransfer = new LessonTransferBatch(transferEvent, transferOperation, UUID.randomUUID(), actorId,
                "d".repeat(64), emptyBinding + 1, groupId, subjectId, semesterId, 1,
                emptyBinding + 2, emptyBinding + 3, LocalDate.of(9990, 1, 2), 1,
                LocalDate.of(9990, 1, 3), 2, 0, 1,
                List.of(new LessonTransferBatch.Binding(emptyBinding, actorId, emptyRequest, null,
                        java.util.HexFormat.of().formatHex(hash), "PENDING", 1)), "e".repeat(64));
        transferCoordinator.applyBatch(acceptedTransfer);
        transferCoordinator.applyBatch(acceptedTransfer);
        assertThat(jdbcTemplate.queryForObject("SELECT result FROM lesson_transfer_receipts "
                + "WHERE operation_id = ? AND batch_index = 0", String.class, transferOperation)).isEqualTo("APPLIED");
        var resolution = deletionBarrier.preparePendingBindingResolution(operationId, semesterId,
                started.getStateVersion(), emptyBinding, emptyBinding + 1, actorId, emptyRequest, hash, 1);
        assertThat(resolution.kind()).isEqualTo(AcademicSemesterArchiveBarrierTransaction.BindingResolutionKind.CANCEL_UNPUBLISHED);
        UUID emptyEvent = UUID.randomUUID();
        deletionBarrier.recordCancellationEvent(operationId, semesterId, started.getStateVersion(),
                emptyBinding, emptyRequest, emptyBinding + 1, 1, emptyEvent);
        archiveConsumer.onEvent(archiveEnvelope(emptyEvent, emptyBinding, actorId, emptyRequest, null));
        assertAppliedEffect(emptyEvent);
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM homework_binding_transfer_markers "
                + "WHERE binding_id = ?", String.class, emptyBinding)).isEqualTo("CANCELLED_UNPUBLISHED");
        assertThat(jdbcTemplate.queryForObject("SELECT resolution_state FROM academic_semester_archive_publication_admissions "
                + "WHERE operation_id = ? AND binding_id = ?", String.class, operationId, emptyBinding))
                .isEqualTo("CANCELLED_UNPUBLISHED");
        assertThat(deletionBarrier.prepareDelete(operationId, semesterId, started.getStateVersion())).isNull();
        assertThat(deletionBarrier.sealDelete(operationId, semesterId, started.getStateVersion())).isNull();
        var lateTransfer = new LessonTransferBatch(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), actorId,
                "f".repeat(64), emptyBinding + 11, groupId, subjectId, semesterId, 1,
                emptyBinding + 12, emptyBinding + 13, LocalDate.of(9990, 1, 2), 1,
                LocalDate.of(9990, 1, 3), 2, 0, 1,
                List.of(new LessonTransferBatch.Binding(emptyBinding + 20, actorId, UUID.randomUUID(), null,
                        java.util.HexFormat.of().formatHex(hash), "PENDING", 1)), "e".repeat(64));
        assertThatThrownBy(() -> transferCoordinator.applyBatch(lateTransfer)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> archiveConsumer.onEvent(archiveEnvelope(UUID.randomUUID(), bindingId,
                actorId, requestKey, homeworkId))).isInstanceOf(ConflictException.class);
        deletionCommands.beginDeleteRelease(operationId, "STALE_PREVIEW");
        deletionBarrier.releaseDelete(operationId, semesterId, started.getStateVersion());
        for (var participant : SemesterArchiveCommandTransaction.Participant.values()) {
            deletionCommands.recordDeleteParticipant(operationId, semesterId, started.getStateVersion(), participant,
                    SemesterArchiveParticipantCommand.RELEASE_DELETE, SemesterArchiveParticipantStatus.RELEASED, null,
                    participant == SemesterArchiveCommandTransaction.Participant.ACADEMIC
                            ? started.getAcademicParticipantDigest() : REMOTE_DIGEST,
                    participant == SemesterArchiveCommandTransaction.Participant.ACADEMIC
                            ? new SemesterDeletionCounts(0, 0, 0, 0, 1, 0, 0) : ZERO_COUNTS);
        }
        deletionCommands.completeDeleteCancellation(operationId);
    }

    private Map<String, Object> archiveEnvelope(UUID eventId, long bindingId, long actorId,
                                                UUID requestKey, Long homeworkId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("binding_id", bindingId); payload.put("actor_id", actorId);
        payload.put("request_key", requestKey.toString()); payload.put("semester_id", semesterId);
        payload.put("occurrence_id", bindingId + 1); payload.put("lesson_id", bindingId + 2);
        payload.put("homework_id", homeworkId); payload.put("binding_revision", 2L);
        return Map.of("event_type", "homework.binding.archived", "event_id", eventId.toString(),
                "occurred_at", "2026-10-01T09:00:00Z", "event_version", 1,
                "trace_id", UUID.randomUUID().toString(), "source", "schedule-service", "payload", payload);
    }

    private void assertAppliedEffect(UUID sourceEventId) {
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM academic_semester_archive_effect_receipts "
                + "WHERE source_event_id = ?", String.class, sourceEventId)).isEqualTo("APPLIED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM academic_outbox "
                + "WHERE event_type = 'semester.archive.effect.ack' AND payload #>> '{payload,source_event_id}' = ?",
                Long.class, sourceEventId.toString())).isEqualTo(1L);
    }

    private void stubScheduleDeleteParticipant() {
        when(scheduleClient.setSemesterDeletionBarrier(any(UUID.class), anyLong(), anyLong(),
                any(SemesterArchiveParticipantCommand.class), anyString())).thenAnswer(invocation -> {
            SemesterArchiveParticipantCommand command = invocation.getArgument(3);
            return SetSemesterArchiveBarrierResponse.newBuilder()
                    .setOperationId(invocation.<UUID>getArgument(0).toString())
                    .setSemesterId(invocation.<Long>getArgument(1)).setStateVersion(invocation.<Long>getArgument(2))
                    .setParticipantDigest(invocation.getArgument(4))
                    .setState(command == SemesterArchiveParticipantCommand.RELEASE_DELETE
                            ? SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_RELEASED
                            : command == SemesterArchiveParticipantCommand.COMMIT_DELETE
                                ? SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_DELETED
                                : SemesterArchiveParticipantState.SEMESTER_ARCHIVE_PARTICIPANT_READY).build();
        });
    }

    private long adminId() {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE login = 'admin'", Long.class);
    }

    private SemesterDeletionPreviewService.Snapshot deletionSnapshot(long id) {
        Semester semester = semesterRepository.findById(id).orElseThrow();
        var local = deletionSnapshots.read(id);
        return new SemesterDeletionPreviewService.Snapshot(id, semester.getName(),
                SemesterDeletionPreviewService.priorState(semester), semester.getStateVersion(), "a".repeat(64),
                new SemesterDeletionCounts(0, 0, 0, local.assignments(), local.homeworks(), 0, 0),
                local.participantDigest(), REMOTE_DIGEST, REMOTE_DIGEST, 0, 0);
    }

    private void readyReceipt(SemesterArchiveOperation operation, SemesterArchiveCommandTransaction.Participant participant,
                               SemesterArchiveParticipantCommand command) {
        deletionCommands.recordDeleteParticipant(operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                participant, command, SemesterArchiveParticipantStatus.READY, null,
                participant == SemesterArchiveCommandTransaction.Participant.ACADEMIC
                        ? operation.getAcademicParticipantDigest() : REMOTE_DIGEST,
                participant == SemesterArchiveCommandTransaction.Participant.ACADEMIC
                        ? new SemesterDeletionCounts(0, 0, 0, operation.getAssignmentsCount(), operation.getHomeworksCount(), 0, 0)
                        : ZERO_COUNTS);
    }

    private long deletionCommandCount(UUID operationId, SemesterArchiveParticipantCommand command) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM academic_outbox
                 WHERE event_type = 'semester.archive.participant.command'
                   AND payload #>> '{payload,operation_id}' = ?
                   AND payload #>> '{payload,command}' = ?
                """, Long.class, operationId.toString(), command.name());
    }

    private Map<String, Object> deletionAttendanceEnvelope(UUID operationId, long stateVersion,
            SemesterArchiveParticipantCommand command, SemesterArchiveParticipantStatus status) {
        return deletionAttendanceEnvelope(operationId, stateVersion, command, status, REMOTE_DIGEST, null, 0);
    }

    private Map<String, Object> deletionAttendanceEnvelope(UUID operationId, long stateVersion,
            SemesterArchiveParticipantCommand command, SemesterArchiveParticipantStatus status,
            String digest, String reason, long attendanceMarks) {
        Map<String, Object> counts = Map.of("scheduleTemplates", 0, "oneOffLessons", 0, "lessons", 0,
                "assignments", 0, "homeworks", 0, "attendanceMarks", attendanceMarks, "studentRequests", 0);
        Map<String, Object> payload = new HashMap<>(Map.of("operation_id", operationId.toString(), "semester_id", semesterId,
                "state_version", stateVersion, "command", command.name(), "status", status.name(),
                "participant_digest", digest, "counts", counts));
        if (reason != null) payload.put("blocking_reason", reason);
        return Map.of("event_type", "semester.archive.participant.ack", "event_id", UUID.randomUUID().toString(),
                "occurred_at", "2026-10-01T09:00:00Z", "event_version", 1,
                "trace_id", UUID.randomUUID().toString(), "source", "attendance-service", "payload", payload);
    }

    private void releasedAttendance(UUID operationId) {
        var operation = deletionCommands.find(operationId);
        deletionCommands.recordDeleteParticipant(operationId, operation.getSemesterId(), operation.getStateVersion(),
                SemesterArchiveCommandTransaction.Participant.ATTENDANCE, SemesterArchiveParticipantCommand.RELEASE_DELETE,
                SemesterArchiveParticipantStatus.RELEASED, null, REMOTE_DIGEST, ZERO_COUNTS);
    }

    /** Empty remote domains are simulated; central deletion and local guards always use the real protocol. */
    private void deleteEmptyFixtureThroughProtocol(long id) {
        var existing = semesterRepository.findById(id);
        if (existing.isEmpty()) return;
        Semester semester = existing.get();
        if (semester.getArchiveTransition() == SemesterTransition.ARCHIVING) {
            semester = semesterService.completeArchiveTransition(id, semester.getStateVersion());
        }
        if (semester.getArchiveTransition() == SemesterTransition.RESTORING) {
            semester = semesterService.completeRestoreTransition(id, semester.getStateVersion());
        }
        if (semester.isReleasePending()) {
            semester = semesterService.completeRestoreRelease(id, semester.getStateVersion());
        }
        long usersBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class);
        long groupsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM groups", Long.class);
        UUID operationId = UUID.randomUUID();
        var preview = deletionSnapshot(id);
        var operation = deletionCommands.startDelete(id, adminId(), operationId, operationId, preview);
        assertThat(deletionBarrier.prepareDelete(operationId, id, operation.getStateVersion())).isNull();
        assertThat(deletionBarrier.sealDelete(operationId, id, operation.getStateVersion())).isNull();
        for (var participant : SemesterArchiveCommandTransaction.Participant.values()) {
            readyReceipt(operation, participant, SemesterArchiveParticipantCommand.SEAL_DELETE);
        }
        deletionCommands.beginIrreversibleDelete(operationId);
        for (var participant : List.of(SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                SemesterArchiveCommandTransaction.Participant.ATTENDANCE)) {
            deletionCommands.recordDeleteParticipant(operationId, id, operation.getStateVersion(), participant,
                    SemesterArchiveParticipantCommand.COMMIT_DELETE, SemesterArchiveParticipantStatus.DELETED,
                    null, REMOTE_DIGEST, ZERO_COUNTS);
        }
        deletionCommands.completeDelete(operationId);
        assertThat(semesterRepository.findById(id)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(usersBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM groups", Long.class)).isEqualTo(groupsBefore);
        assertThat(deletionCommands.completeDelete(operationId).getDeletePhase()).isEqualTo(SemesterDeletionPhase.COMPLETED);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO teacher_subject_groups "
                + "(teacher_id, subject_id, group_id, semester_id) VALUES (1, 1, 1, ?)", id))
                .isInstanceOf(DataAccessException.class);
    }

    private SemesterStateResponse state(long id) {
        AtomicReference<SemesterStateResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        grpcHandler.getSemesterState(SemesterStateRequest.newBuilder().setSemesterId(id).build(),
                new StreamObserver<>() {
                    @Override public void onNext(SemesterStateResponse value) { response.set(value); }
                    @Override public void onError(Throwable error) { failure.set(error); }
                    @Override public void onCompleted() { }
                });
        if (failure.get() != null) throw Status.fromThrowable(failure.get()).asRuntimeException();
        assertThat(response.get()).isNotNull();
        return response.get();
    }

    private long insertInactiveFixture(String name) {
        int fixtureYear = 9990 + fixtureIds.size();
        long id = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class, name, LocalDate.of(fixtureYear, 1, 1), LocalDate.of(fixtureYear, 6, 30));
        fixtureIds.add(id);
        return id;
    }

    private List<Long> activeSemesterIds() {
        return jdbcTemplate.queryForList(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id", Long.class);
    }

    private long latestOutboxId() {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM academic_outbox", Long.class);
    }

    private List<Long> archivedEventIdsAfter(long outboxId) {
        return jdbcTemplate.queryForList("""
                SELECT (payload -> 'payload' ->> 'semester_id')::bigint
                FROM academic_outbox
                WHERE id > ? AND event_type = 'semester.archived'
                ORDER BY id
                """, Long.class, outboxId);
    }

    private static <T> T awaitStartThenRun(CountDownLatch ready,
                                           CountDownLatch start,
                                           java.util.concurrent.Callable<T> operation)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("semester archive race barrier timed out");
        }
        return operation.call();
    }
}
