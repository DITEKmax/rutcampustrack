package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveAction;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveOperationState;
import ru.rutcampustrack.academic.contract.enums.SemesterArchiveParticipantStatus;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;

import java.util.UUID;

/** Stable progress snapshot for one archive or restore command. */
@Schema(description = "Состояние одной команды архивации или восстановления семестра")
public record SemesterArchiveOperationResponse(
        UUID operationId,
        long semesterId,
        SemesterArchiveAction action,
        SemesterArchiveOperationState operationState,
        boolean retryable,
        long stateVersion,
        boolean active,
        boolean archived,
        SemesterTransition transition,
        boolean releasePending,
        SemesterArchiveParticipantStatus academic,
        SemesterArchiveParticipantStatus schedule,
        SemesterArchiveParticipantStatus attendance,
        String blockingReason) {
}
