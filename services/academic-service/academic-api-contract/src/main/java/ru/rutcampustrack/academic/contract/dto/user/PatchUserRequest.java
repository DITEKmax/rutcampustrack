package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;

/**
 * Request DTO for partial update of a user (PATCH semantics).
 * All fields are optional — only non-null fields are applied. For profile
 * fields, an empty middleName clears the patronymic; empty employeeNumber
 * clears the value only when the user has no TEACHER grant.
 */
@Schema(description = "Частичное обновление пользователя (PATCH)")
public record PatchUserRequest(

        @Schema(description = "Фамилия", example = "Иванов", maxLength = 128)
        @Size(max = 128)
        String lastName,

        @Schema(description = "Имя", example = "Иван", maxLength = 128)
        @Size(max = 128)
        String firstName,

        @Schema(description = "Отчество; пустая строка очищает значение", example = "Иванович", maxLength = 128)
        @Size(max = 128)
        String middleName,

        @Schema(description = "Флаг старосты (только для STUDENT)", example = "true")
        Boolean isHeadman,

        @Schema(description = "Новый ID группы", example = "42")
        Long groupId,

        @Schema(description = "Табельный номер; пустая строка очищает значение, кроме аккаунтов с ролью TEACHER", example = "EMP-00123", maxLength = 32)
        @Size(max = 32)
        String employeeNumber,

        @Schema(description = "Telegram user ID", example = "123456789")
        Long telegramId,

        @Schema(description = "Статус учётной записи", example = "ACTIVE")
        AccountStatus status
) {}
