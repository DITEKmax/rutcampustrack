package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.academic.contract.enums.UserRole;

/**
 * Request DTO for full replacement update of a user (PUT semantics, ADMIN only).
 * All fields are required (middleName опционально — отчества может не быть).
 */
public record UpdateUserRequest(

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

        Long groupId,

        String employeeNumber,

        Long telegramId
) {}
