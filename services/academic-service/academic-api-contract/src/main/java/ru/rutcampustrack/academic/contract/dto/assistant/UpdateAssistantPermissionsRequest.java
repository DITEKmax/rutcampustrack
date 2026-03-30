package ru.rutcampustrack.academic.contract.dto.assistant;

import jakarta.validation.constraints.NotEmpty;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.util.List;

public record UpdateAssistantPermissionsRequest(
        @NotEmpty(message = "Список разрешений не может быть пустым")
        List<AssistantPermission> permissions
) {}
