package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterTransition;

/** Current authoritative semester state, optionally with the latest command snapshot. */
@Schema(description = "Текущее состояние семестра и последняя команда архивации")
public record SemesterArchiveStatusResponse(
        long semesterId,
        long stateVersion,
        boolean active,
        boolean archived,
        SemesterTransition transition,
        boolean releasePending,
        boolean writeBlocked,
        SemesterArchiveOperationResponse operation) {
}
