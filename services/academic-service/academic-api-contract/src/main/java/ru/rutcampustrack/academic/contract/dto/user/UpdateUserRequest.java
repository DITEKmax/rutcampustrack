package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Request DTO for full replacement update of a user (PUT semantics, ADMIN only).
 * All fields are required (middleName опционально — отчества может не быть).
 */
@Schema(description = "Полное обновление пользователя (PUT, ADMIN-only)")
public record UpdateUserRequest(

        @Schema(description = "Фамилия", example = "Иванов",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank(message = "Фамилия обязательна")
        @Size(max = 128)
        String lastName,

        @Schema(description = "Имя", example = "Иван",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank(message = "Имя обязательно")
        @Size(max = 128)
        String firstName,

        @Schema(description = "Отчество (опционально)", example = "Иванович", maxLength = 128)
        @Size(max = 128)
        String middleName,

        @Schema(description = "Роль пользователя",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "STUDENT")
        @NotNull(message = "Роль обязательна")
        UserRole role,

        @Schema(description = "ID группы (обязателен для STUDENT)", example = "42")
        Long groupId,

        @Schema(description = "Табельный номер (обязателен для TEACHER)", example = "EMP-00123")
        String employeeNumber,

        @Schema(description = "Telegram user ID", example = "123456789")
        Long telegramId
) {}
