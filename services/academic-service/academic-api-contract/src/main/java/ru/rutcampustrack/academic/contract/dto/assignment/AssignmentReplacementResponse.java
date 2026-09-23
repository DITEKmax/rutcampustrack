package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "AssignmentReplacementResponse")
public record AssignmentReplacementResponse(
        UUID operationId,
        Long sourceAssignmentId,
        Long targetAssignmentId,
        Long sourceTeacherId,
        Long targetTeacherId,
        Long subjectId,
        Long groupId,
        Long semesterId,
        String lessonType,
        LocalDate effectiveFrom,
        LocalDate sourceValidUntilExclusive,
        LocalDate targetValidUntilExclusive,
        String state,
        String scheduleReceiptState,
        long movedCount,
        long skippedCount
) { }
