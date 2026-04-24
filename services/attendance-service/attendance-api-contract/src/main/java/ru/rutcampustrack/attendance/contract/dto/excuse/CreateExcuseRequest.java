package ru.rutcampustrack.attendance.contract.dto.excuse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.attendance.contract.enums.ExcuseType;

import java.util.List;

/**
 * Request DTO for POST /attendance/excuses (D-04, D-15).
 * Java record — no Lombok per contract module rules.
 */
@Schema(description = "Запрос на создание excuse-тикета (студент → староста)")
public record CreateExcuseRequest(

        @Schema(description = "Список ID пар, для которых запрашивается уважительная",
                example = "[12345, 12346]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<Long> lessonIds,

        @Schema(description = "Тип уважительной причины",
                example = "MEDICAL",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ExcuseType excuseType,

        @Schema(description = "Комментарий студента (до 1000 символов)",
                example = "Болезнь",
                maxLength = 1000)
        @Size(max = 1000)
        String comment
) {}
