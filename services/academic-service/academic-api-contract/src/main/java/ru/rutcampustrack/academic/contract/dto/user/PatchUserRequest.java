package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

/**
 * Request DTO for partial update of a user (PATCH semantics).
 * All fields are optional — only non-null fields are applied.
 */
@Schema(description = "Частичное обновление пользователя (PATCH)")
public record PatchUserRequest(

        @Schema(description = "Фамилия", example = "Иванов", maxLength = 128)
        @Size(max = 128)
        String lastName,

        @Schema(description = "Имя", example = "Иван", maxLength = 128)
        @Size(max = 128)
        String firstName,

        @Schema(description = "Отчество", example = "Иванович", maxLength = 128)
        @Size(max = 128)
        String middleName,

        @Schema(description = "Флаг старосты (только для STUDENT)", example = "true")
        Boolean isHeadman,

        @Schema(description = "Новый ID группы", example = "42")
        Long groupId,

        @Schema(description = "Табельный номер", example = "EMP-00123")
        String employeeNumber,

        @Schema(description = "Telegram user ID", example = "123456789")
        Long telegramId,

        @Schema(description = "Статус учётной записи", example = "ACTIVE")
        AccountStatus status
) {}
