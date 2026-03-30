package ru.rutcampustrack.academic.contract.dto.assistant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.util.List;

/**
 * Request DTO for assigning a headman assistant with initial permissions.
 */
public record AssignAssistantRequest(

        @NotNull(message = "ID студента обязателен")
        Long studentId,

        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @NotEmpty(message = "Список прав обязателен")
        List<AssistantPermission> permissions
) {}
