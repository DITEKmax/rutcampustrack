package ru.rutcampustrack.academic.semester;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveOperationResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterArchiveStatusResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.repository.SemesterArchiveOperationRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.util.UUID;

@Service
public class SemesterArchiveService {

    private final RequestContext requestContext;
    private final SemesterArchiveCommandTransaction commandTransaction;
    private final SemesterArchiveOperationRepository operationRepository;
    private final SemesterRepository semesterRepository;
    private final SemesterArchiveCoordinator coordinator;

    public SemesterArchiveService(RequestContext requestContext,
                                  SemesterArchiveCommandTransaction commandTransaction,
                                  SemesterArchiveOperationRepository operationRepository,
                                  SemesterRepository semesterRepository,
                                  SemesterArchiveCoordinator coordinator) {
        this.requestContext = requestContext;
        this.commandTransaction = commandTransaction;
        this.operationRepository = operationRepository;
        this.semesterRepository = semesterRepository;
        this.coordinator = coordinator;
    }

    public SemesterArchiveOperationResponse archive(long semesterId, UUID idempotencyKey) {
        return start(semesterId, idempotencyKey, SemesterArchiveAction.ARCHIVE);
    }

    public SemesterArchiveOperationResponse restore(long semesterId, UUID idempotencyKey) {
        return start(semesterId, idempotencyKey, SemesterArchiveAction.RESTORE);
    }

    public SemesterArchiveStatusResponse status(long semesterId) {
        Semester semester = semesterRepository.findByIdUncached(semesterId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester", "id", semesterId));
        SemesterArchiveOperation latestOperation = operationRepository
                .findTopBySemesterIdOrderByCreatedAtDescOperationIdDesc(semesterId).orElse(null);
        if (latestOperation != null
                && latestOperation.getOperationState() != ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED) {
            coordinator.advance(latestOperation.getOperationId());
            latestOperation = operationRepository.findById(latestOperation.getOperationId()).orElse(latestOperation);
            semester = semesterRepository.findByIdUncached(semesterId).orElse(semester);
        }
        SemesterArchiveOperationResponse latest = latestOperation == null ? null : toResponse(latestOperation);
        return new SemesterArchiveStatusResponse(
                semester.getId(), semester.getStateVersion(), semester.isActive(), semester.isArchived(),
                semester.getArchiveTransition(), semester.isReleasePending(),
                semester.isArchived() || semester.isReleasePending()
                        || semester.getArchiveTransition() != ru.rutcampustrack.academic.contract.enums.SemesterTransition.NONE,
                latest);
    }

    public SemesterArchiveOperationResponse getOperation(UUID operationId) {
        SemesterArchiveOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Semester archive operation", "operationId", operationId));
        if (operation.getOperationState() != ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED) {
            coordinator.advance(operationId);
            operation = operationRepository.findById(operationId).orElse(operation);
        }
        return toResponse(operation);
    }

    private SemesterArchiveOperationResponse start(long semesterId,
                                                    UUID idempotencyKey,
                                                    SemesterArchiveAction action) {
        Long actorId = requestContext.getUserId();
        if (actorId == null || actorId <= 0) {
            throw new AccessDeniedException("Для команды архивации требуется аутентифицированный администратор");
        }
        SemesterArchiveOperation operation = commandTransaction.startOrReplay(
                semesterId, actorId, idempotencyKey, action);
        if (operation.getOperationState() == ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED
                || (operation.getOperationState() == ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.ERROR
                && !operation.isRetryable())) {
            return toResponse(operation);
        }
        return coordinator.advance(operation.getOperationId());
    }

    static SemesterArchiveOperationResponse toResponse(SemesterArchiveOperation operation) {
        return new SemesterArchiveOperationResponse(
                operation.getOperationId(), operation.getSemesterId(), operation.getAction(),
                operation.getOperationState(), operation.isRetryable(), operation.getStateVersion(),
                operation.isActive(), operation.isArchived(), operation.getTransition(),
                operation.isReleasePending(), operation.getAcademic(), operation.getSchedule(),
                operation.getAttendance(), operation.getBlockingReason());
    }
}
