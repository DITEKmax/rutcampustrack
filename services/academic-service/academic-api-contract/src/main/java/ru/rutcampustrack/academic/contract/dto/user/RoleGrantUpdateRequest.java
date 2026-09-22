package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;

/** Atomic add/update command for one ADMIN-managed base role grant. */
@Schema(name = "RoleGrantUpdateRequest")
public record RoleGrantUpdateRequest(
        @NotNull(message = "Статус роли обязателен")
        RoleGrantStatus status,

        @Schema(description = "Группа только для добавления STUDENT")
        @Positive(message = "ID группы должен быть положительным")
        Long groupId,

        @Schema(description = "Табельный номер при добавлении TEACHER")
        @Size(max = 32)
        String employeeNumber,

        @Schema(description = "Telegram ID при добавлении STUDENT")
        @Positive(message = "Telegram ID должен быть положительным")
        Long telegramId
) {}
