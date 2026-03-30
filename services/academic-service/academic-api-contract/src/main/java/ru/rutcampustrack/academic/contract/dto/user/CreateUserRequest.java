package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.UserRole;

public record CreateUserRequest(
        @NotBlank(message = "Отображаемое имя не может быть пустым")
        String displayName,

        @NotNull(message = "Роль пользователя обязательна")
        UserRole role,

        Long groupId,
        String employeeNumber,
        Long telegramId
) {}
