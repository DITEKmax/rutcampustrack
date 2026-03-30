package ru.rutcampustrack.academic.contract.dto.assistant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.util.List;

public record AssignAssistantRequest(
        @NotNull(message = "ID студента обязателен")
        Long studentId,

        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @NotEmpty(message = "Список разрешений не может быть пустым")
        List<AssistantPermission> permissions
) {}
