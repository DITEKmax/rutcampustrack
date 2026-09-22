package ru.rutcampustrack.attendance.contract.dto.headman;

import java.time.Instant;
import java.time.LocalDate;

public record HeadmanRequestSummaryResponse(
        String id,
        String kind,
        String status,
        Long studentId,
        String studentName,
        String reason,
        String comment,
        LocalDate coverageStart,
        LocalDate coverageEnd,
        int lessonCount,
        int alreadyMarkedCount,
        boolean hasAttachments,
        Instant createdAt,
        Instant updatedAt,
        Long decisionBy,
        Instant decisionAt,
        String decisionComment
) {
}
