package ru.rutcampustrack.attendance.contract.dto.marking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Single entry in a batch mark request (M05 D7 / P2-10/4).
 * Все три поля required. CANCELLED status отклоняется сервисом (D-14).
 */
@Schema(description = "Одна отметка в пакете: ID пары, ID студента, статус")
public record MarkBatchItem(
        @Schema(description = "ID пары", example = "12345",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long lessonId,

        @Schema(description = "ID студента", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long userId,

        @Schema(description = "Статус посещаемости (CANCELLED отклоняется сервисом)",
                example = "PRESENT",
                allowableValues = {"PRESENT", "ABSENT", "EXCUSED", "FREE_ATTENDANCE"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull AttendanceStatus status
) {}
