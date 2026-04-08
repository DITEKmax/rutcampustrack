package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Request DTO for full replacement update of a user (PUT semantics, ADMIN only).
 * All fields are required.
 */
public record UpdateUserRequest(

        @NotBlank(message = "Имя пользователя обязательно")
        @Size(max = 255)
        String displayName,

        @NotNull(message = "Роль обязательна")
        UserRole role,

        Long groupId,

        String employeeNumber,

        Long telegramId
) {}
