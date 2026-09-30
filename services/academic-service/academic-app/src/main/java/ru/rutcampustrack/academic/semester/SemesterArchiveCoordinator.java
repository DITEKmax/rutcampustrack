package ru.rutcampustrack.academic.semester;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionCounts;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.academic.homework.HomeworkPublicationPersistence;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState;
import ru.rutcampustrack.schedule.grpc.SetSemesterArchiveBarrierResponse;

import java.util.UUID;

/** Advances the durable central operation only from exact, persisted participant receipts. */
@Service
public class SemesterArchiveCoordinator {

    private final SemesterArchiveCommandTransaction commands;
    private final AcademicSemesterArchiveBarrierTransaction academicBarrier;
    private final ScheduleGrpcClient scheduleClient;
    private final HomeworkPublicationPersistence publicationPersistence;
    private final SemesterDeletionPreviewService deletionPreviews;

    public SemesterArchiveCoordinator(SemesterArchiveCommandTransaction commands,
                                      AcademicSemesterArchiveBarrierTransaction academicBarrier,
                                      ScheduleGrpcClient scheduleClient,
                                      HomeworkPublicationPersistence publicationPersistence) {
        this(commands, academicBarrier, scheduleClient, publicationPersistence, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public SemesterArchiveCoordinator(SemesterArchiveCommandTransaction commands,
                                      AcademicSemesterArchiveBarrierTransaction academicBarrier,
                                      ScheduleGrpcClient scheduleClient,
                                      HomeworkPublicationPersistence publicationPersistence,
                                      SemesterDeletionPreviewService deletionPreviews) {
        this.commands = commands;
        this.academicBarrier = academicBarrier;
        this.scheduleClient = scheduleClient;
        this.publicationPersistence = publicationPersistence;
        this.deletionPreviews = deletionPreviews;
    }

    /** Retries a small ordered batch of live operations without depending on another client request. */
    @Scheduled(fixedDelayString = "${semester.archive.retry-delay-ms:3000}")
    public void retryPendingOperations() {
        for (UUID operationId : commands.retryableOperationIds()) {
            advance(operationId);
        }
    }

    /** Suspends afterCommit resources so each durable step starts its own transaction. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SemesterArchiveOperationResponse advance(UUID operationId) {
        SemesterArchiveOperation operation = commands.find(operationId);
        if (operation.getOperationState()
                == ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED) {
            return SemesterArchiveService.toResponse(operation);
        }
        commands.markRetrying(operationId);
        try {
            if (operation.getAction() == SemesterArchiveAction.ARCHIVE) return advanceArchive(operationId);
            if (operation.getAction() == SemesterArchiveAction.RESTORE) return advanceRestore(operationId);
            advanceDelete(operationId);
            return SemesterArchiveService.toResponse(commands.find(operationId));
        } catch (RuntimeException failure) {
            SemesterArchiveOperation failed = commands.markRetryableError(operationId, safeReason(failure));
            return SemesterArchiveService.toResponse(failed);
        }
    }

    private void advanceDelete(UUID operationId) {
        SemesterArchiveOperation operation = commands.find(operationId);
        if (operation.getDeletePhase() == SemesterDeletionPhase.PREPARING) {
            if (operation.getPrepareExpiresAt() != null
                    && operation.getPrepareExpiresAt().isBefore(java.time.OffsetDateTime.now())) {
                commands.beginDeleteRelease(operationId, "PREPARE_EXPIRED");
                operation = commands.find(operationId);
            } else {
                retryRequestedCancellations(operation);
                if (operation.getSchedule() != SemesterArchiveParticipantStatus.READY) {
                    var response = scheduleClient.setSemesterDeletionBarrier(operationId,
                            operation.getSemesterId(), operation.getStateVersion(),
                            ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.PREPARE_DELETE,
                            operation.getScheduleParticipantDigest());
                    requireSameOperation(operation, response);
                    commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                            operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                            ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.PREPARE_DELETE,
                            fromDeletionWire(response.getState()), response.getBlockingReason(),
                            response.getParticipantDigest(), scheduleCounts(response));
                    if (response.hasPendingBinding()) resolvePendingBinding(operation, response);
                }
                operation = commands.find(operationId);
                redriveMaterializedPublications(operation);
                operation = commands.find(operationId);
                if (operation.getAcademic() != SemesterArchiveParticipantStatus.READY) {
                    String reason = academicBarrier.prepareDelete(operationId,
                            operation.getSemesterId(), operation.getStateVersion());
                    commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                            operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.ACADEMIC,
                            ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.PREPARE_DELETE,
                            reason == null ? SemesterArchiveParticipantStatus.READY
                                    : SemesterArchiveParticipantStatus.PENDING,
                            reason, operation.getAcademicParticipantDigest(), academicCounts(operation));
                }
                operation = commands.find(operationId);
                if (operation.getAttendance() != SemesterArchiveParticipantStatus.READY
                        && !academicAndScheduleReady(operation)) {
                    commands.enqueueParticipantCommand(operationId,
                            ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.PREPARE_DELETE);
                }
                operation = commands.find(operationId);
                if (academicAndScheduleReady(operation)) {
                    if (!operation.isScheduleSealed()) {
                        var response = scheduleClient.setSemesterDeletionBarrier(operationId,
                                operation.getSemesterId(), operation.getStateVersion(),
                                ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.SEAL_DELETE,
                                operation.getScheduleParticipantDigest());
                        requireSameOperation(operation, response);
                        commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                                operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                                ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.SEAL_DELETE,
                                fromDeletionWire(response.getState()), response.getBlockingReason(),
                                response.getParticipantDigest(), scheduleCounts(response));
                    }
                    operation = commands.find(operationId);
                    if (!operation.isAcademicSealed()) {
                        String reason = academicBarrier.sealDelete(operationId,
                                operation.getSemesterId(), operation.getStateVersion());
                        commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                                operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.ACADEMIC,
                                ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.SEAL_DELETE,
                                reason == null ? SemesterArchiveParticipantStatus.READY
                                        : SemesterArchiveParticipantStatus.PENDING,
                                reason, operation.getAcademicParticipantDigest(), academicCounts(operation));
                    }
                    operation = commands.find(operationId);
                    if (operation.isAcademicSealed() && operation.isScheduleSealed()
                            && !operation.isAttendanceSealed()) {
                        commands.enqueueParticipantCommand(operationId,
                                ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.SEAL_DELETE);
                    }
                    operation = commands.find(operationId);
                    if (operation.isAcademicSealed() && operation.isScheduleSealed()
                            && operation.isAttendanceSealed()) {
                        SemesterDeletionPreviewService.Snapshot sealed = deletionPreviews.underFence(
                                operation.getSemesterId(), operation.getSemesterName(),
                                operation.getOriginalStateVersion(), operation.getPriorState());
                        if (!operation.getPreviewDigest().equals(sealed.digest())) {
                            commands.beginDeleteRelease(operationId, "STALE_PREVIEW");
                        } else {
                            commands.beginIrreversibleDelete(operationId);
                        }
                    }
                }
            }
        }

        operation = commands.find(operationId);
        if (operation.getDeletePhase() == SemesterDeletionPhase.RELEASING) {
            if (operation.getSchedule() != SemesterArchiveParticipantStatus.RELEASED) {
                var response = scheduleClient.setSemesterDeletionBarrier(operationId,
                        operation.getSemesterId(), operation.getStateVersion(),
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.RELEASE_DELETE,
                        operation.getScheduleParticipantDigest());
                requireSameOperation(operation, response);
                commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                        operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.RELEASE_DELETE,
                        fromDeletionWire(response.getState()), response.getBlockingReason(),
                        response.getParticipantDigest(), scheduleCounts(response));
            }
            operation = commands.find(operationId);
            if (operation.getAcademic() != SemesterArchiveParticipantStatus.RELEASED) {
                academicBarrier.releaseDelete(operationId, operation.getSemesterId(), operation.getStateVersion());
                commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                        operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.ACADEMIC,
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.RELEASE_DELETE,
                        SemesterArchiveParticipantStatus.RELEASED, null,
                        operation.getAcademicParticipantDigest(), academicCounts(operation));
            }
            operation = commands.find(operationId);
            if (operation.getAttendance() != SemesterArchiveParticipantStatus.RELEASED) {
                commands.enqueueParticipantCommand(operationId,
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.RELEASE_DELETE);
            }
            operation = commands.find(operationId);
            if (operation.getAcademic() == SemesterArchiveParticipantStatus.RELEASED
                    && operation.getSchedule() == SemesterArchiveParticipantStatus.RELEASED
                    && operation.getAttendance() == SemesterArchiveParticipantStatus.RELEASED) {
                commands.completeDeleteCancellation(operationId);
            }
        }

        operation = commands.find(operationId);
        if (operation.getDeletePhase() == SemesterDeletionPhase.DELETING && operation.isIrreversibleIntent()) {
            if (operation.getSchedule() != SemesterArchiveParticipantStatus.DELETED) {
                var response = scheduleClient.setSemesterDeletionBarrier(operationId,
                        operation.getSemesterId(), operation.getStateVersion(),
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.COMMIT_DELETE,
                        operation.getScheduleParticipantDigest());
                requireSameOperation(operation, response);
                commands.recordDeleteParticipant(operationId, operation.getSemesterId(),
                        operation.getStateVersion(), SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.COMMIT_DELETE,
                        fromDeletionWire(response.getState()), response.getBlockingReason(),
                        response.getParticipantDigest(), scheduleCounts(response));
            }
            operation = commands.find(operationId);
            if (operation.getAttendance() != SemesterArchiveParticipantStatus.DELETED) {
                commands.enqueueParticipantCommand(operationId,
                        ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand.COMMIT_DELETE);
            }
            operation = commands.find(operationId);
            if (operation.getSchedule() == SemesterArchiveParticipantStatus.DELETED
                    && operation.getAttendance() == SemesterArchiveParticipantStatus.DELETED) {
                commands.completeDelete(operationId);
            }
        }
    }

    private static boolean academicAndScheduleReady(SemesterArchiveOperation operation) {
        return operation.getAcademic() == SemesterArchiveParticipantStatus.READY
                && operation.getSchedule() == SemesterArchiveParticipantStatus.READY;
    }

    private static SemesterDeletionCounts academicCounts(SemesterArchiveOperation operation) {
        return new SemesterDeletionCounts(0, 0, 0,
                operation.getAssignmentsCount(), operation.getHomeworksCount(), 0, 0);
    }

    private static SemesterDeletionCounts scheduleCounts(
            ru.rutcampustrack.schedule.grpc.SetSemesterArchiveBarrierResponse response) {
        return new SemesterDeletionCounts(response.getScheduleTemplatesCount(),
                response.getOneOffLessonsCount(), response.getLessonsCount(), 0, 0, 0, 0);
    }

    private static SemesterArchiveParticipantStatus fromDeletionWire(
            ru.rutcampustrack.schedule.grpc.SemesterArchiveParticipantState state) {
        return switch (state) {
            case SEMESTER_ARCHIVE_PARTICIPANT_PENDING -> SemesterArchiveParticipantStatus.PENDING;
            case SEMESTER_ARCHIVE_PARTICIPANT_READY -> SemesterArchiveParticipantStatus.READY;
            case SEMESTER_ARCHIVE_PARTICIPANT_RELEASED -> SemesterArchiveParticipantStatus.RELEASED;
            case SEMESTER_ARCHIVE_PARTICIPANT_DELETED -> SemesterArchiveParticipantStatus.DELETED;
            default -> throw new IllegalStateException("Schedule returned an invalid deletion participant state");
        };
    }

    private SemesterArchiveOperationResponse advanceArchive(UUID operationId) {
        SemesterArchiveOperation operation = commands.find(operationId);
        if (operation.getSchedule() != SemesterArchiveParticipantStatus.READY) {
            retryRequestedCancellations(operation);
            SetSemesterArchiveBarrierResponse response = scheduleClient.setSemesterArchiveBarrier(
                    operationId, operation.getSemesterId(), operation.getStateVersion(),
                    SemesterArchiveParticipantCommand.PREPARE_ARCHIVE);
            commands.recordParticipant(operationId, SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                    fromWire(response.getState()), response.getBlockingReason());
            if (response.hasPendingBinding()) {
                resolvePendingBinding(operation, response);
            }
        }

        operation = commands.find(operationId);
        redriveMaterializedPublications(operation);

        operation = commands.find(operationId);
        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY
                && operation.getAcademic() != SemesterArchiveParticipantStatus.READY) {
            String reason = academicBarrier.sealArchive(operationId,
                    operation.getSemesterId(), operation.getStateVersion());
            commands.recordParticipant(operationId, SemesterArchiveCommandTransaction.Participant.ACADEMIC,
                    reason == null ? SemesterArchiveParticipantStatus.READY
                            : SemesterArchiveParticipantStatus.PENDING,
                    reason);
        }

        operation = commands.find(operationId);
        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY
                && operation.getAcademic() == SemesterArchiveParticipantStatus.READY
                && operation.getAttendance() != SemesterArchiveParticipantStatus.READY) {
            commands.enqueueParticipantCommand(operationId, SemesterArchiveParticipantCommand.SEAL_ARCHIVE);
        }
        operation = commands.find(operationId);
        if (operation.getSchedule() == SemesterArchiveParticipantStatus.READY
                && operation.getAcademic() == SemesterArchiveParticipantStatus.READY
                && operation.getAttendance() == SemesterArchiveParticipantStatus.READY) {
            operation = commands.completeArchive(operationId);
        }
        return SemesterArchiveService.toResponse(operation);
    }

    private void resolvePendingBinding(SemesterArchiveOperation operation,
                                       SetSemesterArchiveBarrierResponse scheduleResponse) {
        var pending = scheduleResponse.getPendingBinding();
        var resolution = academicBarrier.preparePendingBindingResolution(
                operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                pending.getBindingId(), pending.getOccurrenceId(), pending.getActorId(),
                UUID.fromString(pending.getRequestKey()), pending.getPayloadHash().toByteArray(),
                pending.getRevision());
        switch (resolution.kind()) {
            case CONFIRM_MATERIALIZED -> {
                SetSemesterArchiveBarrierResponse confirmed = scheduleClient.reconcileArchiveHomeworkBinding(
                        operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                        resolution.bindingId(), resolution.occurrenceId(), resolution.actorId(),
                        resolution.requestKey(), resolution.payloadHash(), resolution.revision(),
                        resolution.homeworkId());
                requireSameOperation(operation, confirmed);
                if (operation.getAction() == SemesterArchiveAction.DELETE) {
                    academicBarrier.markDeletionPublicationDrained(operation.getOperationId(),
                            operation.getSemesterId(), operation.getStateVersion(), resolution.bindingId(),
                            resolution.actorId(), resolution.requestKey(), resolution.payloadHash(),
                            resolution.homeworkId());
                } else {
                    activateAcceptedPublication(resolution.bindingId(), resolution.actorId(),
                            resolution.requestKey(), resolution.payloadHash(), resolution.homeworkId());
                }
            }
            case CANCEL_UNPUBLISHED -> {
                SetSemesterArchiveBarrierResponse cancelled = scheduleClient.reconcileArchiveHomeworkBinding(
                        operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                        resolution.bindingId(), resolution.occurrenceId(), resolution.actorId(),
                        resolution.requestKey(), resolution.payloadHash(), resolution.revision(), null);
                recordCancellation(operation, resolution, cancelled);
            }
            case WAIT_FOR_TRANSFER_RECEIPT, TERMINAL -> {
                // Keep the operation visibly pending; the bounded retry will re-read the durable receipt.
            }
        }
    }

    private void retryRequestedCancellations(SemesterArchiveOperation operation) {
        for (AcademicSemesterArchiveBarrierTransaction.BindingResolution pending
                : academicBarrier.pendingCancellations(operation.getOperationId(), operation.getSemesterId(),
                operation.getStateVersion(), 8)) {
            SetSemesterArchiveBarrierResponse response = scheduleClient.reconcileArchiveHomeworkBinding(
                    operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                    pending.bindingId(), pending.occurrenceId(), pending.actorId(), pending.requestKey(),
                    pending.payloadHash(), pending.revision(), null);
            recordCancellation(operation, pending, response);
        }
    }

    private void recordCancellation(SemesterArchiveOperation operation,
                                    AcademicSemesterArchiveBarrierTransaction.BindingResolution request,
                                    SetSemesterArchiveBarrierResponse response) {
        requireSameOperation(operation, response);
        if (response.getTerminalEventId().isBlank()) {
            throw new IllegalStateException("Schedule cancellation did not return its durable event identity");
        }
        UUID terminalEventId = UUID.fromString(response.getTerminalEventId());
        academicBarrier.recordCancellationEvent(operation.getOperationId(), operation.getSemesterId(),
                operation.getStateVersion(), request.bindingId(), request.requestKey(),
                request.occurrenceId(), request.revision(), terminalEventId);
        SemesterArchiveParticipantStatus status = fromWire(response.getState());
        commands.recordParticipant(operation.getOperationId(), SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                status, status == SemesterArchiveParticipantStatus.PENDING
                        ? "Ожидается применение отмены и точный ACK Schedule effect" : null);
    }

    private void redriveMaterializedPublications(SemesterArchiveOperation operation) {
        for (AcademicSemesterArchiveBarrierTransaction.PendingHomeworkPublication pending
                : academicBarrier.pendingMaterializedPublications(operation.getOperationId(),
                operation.getSemesterId(), operation.getStateVersion(), 8)) {
            SetSemesterArchiveBarrierResponse confirmed = scheduleClient.reconcileArchiveHomeworkBinding(
                    operation.getOperationId(), operation.getSemesterId(), operation.getStateVersion(),
                    pending.bindingId(), 0, pending.actorId(), pending.requestKey(),
                    pending.payloadHash(), 0, pending.homeworkId());
            requireSameOperation(operation, confirmed);
            if (operation.getAction() == SemesterArchiveAction.DELETE) {
                academicBarrier.markDeletionPublicationDrained(operation.getOperationId(),
                        operation.getSemesterId(), operation.getStateVersion(), pending.bindingId(),
                        pending.actorId(), pending.requestKey(), pending.payloadHash(), pending.homeworkId());
            } else {
                activateAcceptedPublication(pending.bindingId(), pending.actorId(),
                        pending.requestKey(), pending.payloadHash(), pending.homeworkId());
            }
        }
    }

    private void activateAcceptedPublication(long bindingId, long actorId, UUID requestKey,
                                             byte[] payloadHash, long homeworkId) {
        var homework = publicationPersistence.activate(homeworkId, actorId, requestKey, bindingId, payloadHash);
        if (homework.getPublicationState() != ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState.ACTIVE) {
            throw new IllegalStateException("Schedule confirmed an archive admission that Academic did not activate");
        }
    }

    private static void requireSameOperation(SemesterArchiveOperation operation,
                                             SetSemesterArchiveBarrierResponse response) {
        if (!operation.getOperationId().toString().equals(response.getOperationId())
                || operation.getSemesterId() != response.getSemesterId()
                || operation.getStateVersion() != response.getStateVersion()) {
            throw new IllegalStateException("Schedule returned a different archive operation identity");
        }
    }

    private SemesterArchiveOperationResponse advanceRestore(UUID operationId) {
        SemesterArchiveOperation operation = commands.find(operationId);
        if (!operation.isReleasePending()) {
            if (operation.getSchedule() != SemesterArchiveParticipantStatus.PREPARED_RESTORE) {
                SetSemesterArchiveBarrierResponse response = scheduleClient.setSemesterArchiveBarrier(
                        operationId, operation.getSemesterId(), operation.getStateVersion(),
                        SemesterArchiveParticipantCommand.PREPARE_RESTORE);
                commands.recordParticipant(operationId,
                        SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                        fromWire(response.getState()), response.getBlockingReason());
            }
            operation = commands.find(operationId);
            if (operation.getSchedule() == SemesterArchiveParticipantStatus.PREPARED_RESTORE
                    && operation.getAcademic() == SemesterArchiveParticipantStatus.PREPARED_RESTORE
                    && operation.getAttendance() == SemesterArchiveParticipantStatus.PREPARED_RESTORE) {
                operation = commands.beginRestoreRelease(operationId);
            }
        }

        operation = commands.find(operationId);
        if (operation.isReleasePending()) {
            if (operation.getSchedule() != SemesterArchiveParticipantStatus.RELEASED) {
                SetSemesterArchiveBarrierResponse response = scheduleClient.setSemesterArchiveBarrier(
                        operationId, operation.getSemesterId(), operation.getStateVersion(),
                        SemesterArchiveParticipantCommand.RELEASE_RESTORE);
                commands.recordParticipant(operationId,
                        SemesterArchiveCommandTransaction.Participant.SCHEDULE,
                        fromWire(response.getState()), response.getBlockingReason());
            }
            operation = commands.find(operationId);
            if (operation.getAcademic() != SemesterArchiveParticipantStatus.RELEASED) {
                academicBarrier.releaseRestore(operationId,
                        operation.getSemesterId(), operation.getStateVersion());
                commands.recordParticipant(operationId,
                        SemesterArchiveCommandTransaction.Participant.ACADEMIC,
                        SemesterArchiveParticipantStatus.RELEASED, null);
            }
            operation = commands.find(operationId);
            if (operation.getSchedule() == SemesterArchiveParticipantStatus.RELEASED
                    && operation.getAcademic() == SemesterArchiveParticipantStatus.RELEASED
                    && operation.getAttendance() == SemesterArchiveParticipantStatus.RELEASED) {
                operation = commands.completeRestoreRelease(operationId);
            }
        }
        return SemesterArchiveService.toResponse(operation);
    }

    private static SemesterArchiveParticipantStatus fromWire(SemesterArchiveParticipantState state) {
        return switch (state) {
            case SEMESTER_ARCHIVE_PARTICIPANT_PENDING -> SemesterArchiveParticipantStatus.PENDING;
            case SEMESTER_ARCHIVE_PARTICIPANT_READY -> SemesterArchiveParticipantStatus.READY;
            case SEMESTER_ARCHIVE_PARTICIPANT_PREPARED_RESTORE ->
                    SemesterArchiveParticipantStatus.PREPARED_RESTORE;
            case SEMESTER_ARCHIVE_PARTICIPANT_RELEASED -> SemesterArchiveParticipantStatus.RELEASED;
            default -> throw new IllegalStateException("Schedule returned an unspecified archive barrier state");
        };
    }

    private static String safeReason(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "Участник архивации временно недоступен";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
