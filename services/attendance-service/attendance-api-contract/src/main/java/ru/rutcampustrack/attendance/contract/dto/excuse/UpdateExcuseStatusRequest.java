package ru.rutcampustrack.attendance.contract.dto.excuse;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;

/**
 * Request DTO for PATCH /attendance/excuses/{id}/status (D-07).
 * Java record — no Lombok per contract module rules.
 * Only APPROVED or REJECTED are valid statuses here (enforced in service).
 */
@Schema(description = "Запрос на обновление статуса excuse-тикета (решение старосты)")
public record UpdateExcuseStatusRequest(

        @Schema(description = "Новый статус тикета (допустимы только APPROVED или REJECTED)",
                example = "APPROVED",
                allowableValues = {"APPROVED", "REJECTED"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        ExcuseTicketStatus status,

        @Schema(description = "Комментарий к решению (до 1000 символов)",
                example = "Подтверждаю, справка приложена",
                maxLength = 1000)
        @Size(max = 1000)
        String decisionComment
) {}
