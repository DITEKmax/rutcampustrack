package ru.rutcampustrack.attendance.contract.dto.marking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;

/**
 * Request DTO for manual attendance marking by headman (D-11).
 * Java record — no Lombok per contract module rules.
 */
@Schema(description = "Запрос на ручную отметку посещаемости старостой")
public record MarkRequest(
        @Schema(description = "Статус посещаемости",
                example = "PRESENT",
                allowableValues = {"PRESENT", "ABSENT", "EXCUSED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull AttendanceStatus status,

        @Schema(description = "Тип уважительной причины; обязателен для EXCUSED",
                example = "ILLNESS")
        ExcuseType excuseType,

        @Schema(description = "Комментарий старосты (до 1000 символов)", maxLength = 1000)
        @Size(max = 1000) String comment
) {
    /** Source-compatible constructor for existing JSON clients and tests. */
    public MarkRequest(AttendanceStatus status) {
        this(status, null, null);
    }
}
