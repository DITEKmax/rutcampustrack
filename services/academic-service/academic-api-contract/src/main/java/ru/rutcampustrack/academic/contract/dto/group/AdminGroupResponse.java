package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** Server-owned row used by the ADMIN group registry. */
@Schema(description = "Строка реестра групп администратора")
public record AdminGroupResponse(
        Long id,
        String name,
        String alphabeticCode,
        String numericCode,
        Integer currentCourse,
        Integer trainingDurationYears,
        String durationStatus,
        AdminGroupStatus status,
        String draftReason,
        long studentCount,
        String headmanFio,
        OffsetDateTime createdAt
) {}
