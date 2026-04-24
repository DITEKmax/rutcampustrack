package ru.rutcampustrack.attendance.contract.dto.marking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

/**
 * Request DTO for manual attendance marking by headman (D-11).
 * Java record — no Lombok per contract module rules.
 */
@Schema(description = "Запрос на ручную отметку посещаемости старостой")
public record MarkRequest(
        @Schema(description = "Статус посещаемости",
                example = "PRESENT",
                allowableValues = {"PRESENT", "ABSENT", "EXCUSED", "FREE_ATTENDANCE", "CANCELLED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull AttendanceStatus status
) {}
