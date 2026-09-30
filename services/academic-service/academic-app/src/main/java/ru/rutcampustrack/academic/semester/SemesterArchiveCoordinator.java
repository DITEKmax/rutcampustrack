package ru.rutcampustrack.academic.semester;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantCommand;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
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

    public SemesterArchiveCoordinator(SemesterArchiveCommandTransaction commands,
                                      AcademicSemesterArchiveBarrierTransaction academicBarrier,
                                      ScheduleGrpcClient scheduleClient,
                                      HomeworkPublicationPersistence publicationPersistence) {
        this.commands = commands;
        this.academicBarrier = academicBarrier;
        this.scheduleClient = scheduleClient;
        this.publicationPersistence = publicationPersistence;
    }

    /** Retries a small ordered batch of live operations without depending on another client request. */
    @Scheduled(fixedDelayString = "${semester.archive.retry-delay-ms:3000}")
    public void retryPendingOperations() {
        for (UUID operationId : commands.retryableOperationIds()) {
            advance(operationId);
        }
    }

    public SemesterArchiveOperationResponse advance(UUID operationId) {
        SemesterArchiveOperation operation = commands.find(operationId);
        if (operation.getOperationState()
                == ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED) {
            return SemesterArchiveService.toResponse(operation);
        }
        commands.markRetrying(operationId);
        try {
            return operation.getAction() == SemesterArchiveAction.ARCHIVE
                    ? advanceArchive(operationId)
                    : advanceRestore(operationId);
        } catch (RuntimeException failure) {
            SemesterArchiveOperation failed = commands.markRetryableError(operationId, safeReason(failure));
            return SemesterArchiveService.toResponse(failed);
        }
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
                activateAcceptedPublication(resolution.bindingId(), resolution.actorId(),
                        resolution.requestKey(), resolution.payloadHash(), resolution.homeworkId());
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
            activateAcceptedPublication(pending.bindingId(), pending.actorId(),
                    pending.requestKey(), pending.payloadHash(), pending.homeworkId());
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
