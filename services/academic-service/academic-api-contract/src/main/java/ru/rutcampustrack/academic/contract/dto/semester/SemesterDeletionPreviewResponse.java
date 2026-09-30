package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SemesterDeletionPriorState;

@Schema(description = "Предварительный расчёт удаления семестра без блокировки записей")
public record SemesterDeletionPreviewResponse(
        long semesterId,
        String semesterName,
        SemesterDeletionPriorState priorState,
        long stateVersion,
        String previewDigest,
        SemesterDeletionCounts counts) {
}
