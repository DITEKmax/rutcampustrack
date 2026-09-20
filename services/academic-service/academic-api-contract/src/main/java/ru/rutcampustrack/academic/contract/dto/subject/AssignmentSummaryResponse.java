package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.time.LocalDate;

/** Stable assignment identity included in a subject read. */
@Schema(name = "AssignmentSummaryResponse")
public record AssignmentSummaryResponse(
        Long id,
        Long teacherId,
        Long semesterId,
        SubjectType lessonType,
        LocalDate validFrom,
        @Schema(nullable = true) LocalDate validUntilExclusive
) {
}
