package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPhase;

import java.util.UUID;

/** Durable, credential-free progress snapshot that remains readable after the semester row is removed. */
@Schema(description = "Состояние операции окончательного удаления семестра")
public record SemesterDeletionOperationResponse(
        UUID operationId,
        long semesterId,
        SemesterDeletionPhase phase,
        boolean retryable,
        long stateVersion,
        SemesterDeletionCounts counts,
        String reason,
        String blockingReason,
        SemesterDeletionPreviewResponse refreshedPreview) {
}
