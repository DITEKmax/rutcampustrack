package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Request DTO for creating a new user (ADMIN only).
 * Auto-generated login and initial password are returned in {@link UserCreatedResponse}.
 */
public record CreateUserRequest(

        @NotBlank(message = "Фамилия обязательна")
        @Size(max = 128)
        String lastName,

        @NotBlank(message = "Имя обязательно")
        @Size(max = 128)
        String firstName,

        @Size(max = 128)
        String middleName,

        @NotNull(message = "Роль обязательна")
        UserRole role,

        /** Required for STUDENT — group the student belongs to. */
        Long groupId,

        /** Required for TEACHER — personnel/employee number. */
        String employeeNumber,

        /** Optional — Telegram user ID for OTP and bot notifications. */
        Long telegramId
) {}
