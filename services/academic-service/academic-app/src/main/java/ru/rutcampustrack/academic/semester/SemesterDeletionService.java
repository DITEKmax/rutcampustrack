package ru.rutcampustrack.academic.semester;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.contract.dto.semester.DeleteSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionOperationResponse;
import ru.rutcampustrack.academic.contract.dto.semester.SemesterDeletionPreviewResponse;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.repository.SemesterArchiveOperationRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.security.MessageDigest;
import java.util.UUID;

/** API-facing lifecycle for preview, password confirmation and durable status polling. */
@Service
public class SemesterDeletionService {

    private final RequestContext requestContext;
    private final AuthSemesterDeletionClient authClient;
    private final SemesterDeletionPreviewService previews;
    private final SemesterArchiveCommandTransaction commands;
    private final SemesterArchiveCoordinator coordinator;
    private final SemesterArchiveOperationRepository operations;

    public SemesterDeletionService(RequestContext requestContext,
                                   AuthSemesterDeletionClient authClient,
                                   SemesterDeletionPreviewService previews,
                                   SemesterArchiveCommandTransaction commands,
                                   SemesterArchiveCoordinator coordinator,
                                   SemesterArchiveOperationRepository operations) {
        this.requestContext = requestContext;
        this.authClient = authClient;
        this.previews = previews;
        this.commands = commands;
        this.coordinator = coordinator;
        this.operations = operations;
    }

    public SemesterDeletionPreviewResponse preview(long semesterId) {
        return previews.preview(semesterId).response();
    }

    public SemesterDeletionOperationResponse confirm(long semesterId, DeleteSemesterRequest request,
                                                       UUID idempotencyKey) {
        Long actorId = requestContext.getUserId();
        if (actorId == null || actorId <= 0) {
            throw new AccessDeniedException("Для удаления требуется аутентифицированный ADMIN");
        }
        SemesterArchiveOperation existing = operations.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            requireSameRequest(existing, semesterId, actorId, request.previewDigest());
            authClient.confirm(semesterId, existing.getOperationId(), request.previewDigest(), request.password());
            return status(existing.getOperationId());
        }

        SemesterDeletionPreviewService.Snapshot snapshot = previews.preview(semesterId);
        if (!MessageDigest.isEqual(snapshot.digest().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                request.previewDigest().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new StaleSemesterDeletionPreviewException(snapshot.response());
        }
        // Auth is deliberately outside the database transaction and bound to the exact key and digest.
        authClient.confirm(semesterId, idempotencyKey, request.previewDigest(), request.password());
        try {
            commands.startDelete(semesterId, actorId, idempotencyKey, idempotencyKey, snapshot);
        } catch (ConflictException conflict) {
            if ("stateVersion".equals(conflict.getField())) {
                throw new StaleSemesterDeletionPreviewException(previews.preview(semesterId).response());
            }
            throw conflict;
        }
        return status(idempotencyKey);
    }

    public SemesterDeletionOperationResponse status(UUID operationId) {
        SemesterArchiveOperation operation = operations.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Semester deletion operation", "operationId", operationId));
        requireDeleteOperation(operation);
        if (operation.getOperationState() != ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState.COMPLETED) {
            coordinator.advance(operationId);
            operation = operations.findById(operationId).orElse(operation);
        }
        SemesterDeletionPreviewResponse refreshed = null;
        if (operation.getDeletePhase() == SemesterDeletionPhase.CANCELLED
                && ("STALE_PREVIEW".equals(operation.getCancelReason())
                || "PREPARE_EXPIRED".equals(operation.getCancelReason()))) {
            refreshed = previews.preview(operation.getSemesterId()).response();
        }
        return toResponse(operation, refreshed);
    }

    private static void requireSameRequest(SemesterArchiveOperation operation, long semesterId,
                                           long actorId, String previewDigest) {
        if (operation.getAction() != SemesterArchiveAction.DELETE
                || operation.getSemesterId() != semesterId || operation.getActorId() != actorId
                || !previewDigest.equals(operation.getPreviewDigest())) {
            throw new ConflictException("idempotencyKey", operation.getIdempotencyKey(),
                    "Ключ идемпотентности уже закреплён за другой командой удаления");
        }
    }

    private static void requireDeleteOperation(SemesterArchiveOperation operation) {
        if (operation.getAction() != SemesterArchiveAction.DELETE || operation.getDeletePhase() == null) {
            throw new ResourceNotFoundException("Semester deletion operation", "operationId",
                    operation.getOperationId());
        }
    }

    private static SemesterDeletionOperationResponse toResponse(SemesterArchiveOperation operation,
                                                                 SemesterDeletionPreviewResponse refreshed) {
        return new SemesterDeletionOperationResponse(operation.getOperationId(), operation.getSemesterId(),
                operation.getDeletePhase(), operation.isRetryable(), operation.getStateVersion(),
                operation.deletionCounts(), operation.getCancelReason(), operation.getBlockingReason(), refreshed);
    }
}
