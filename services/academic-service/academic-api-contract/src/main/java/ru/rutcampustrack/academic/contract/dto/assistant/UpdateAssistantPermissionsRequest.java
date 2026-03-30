package ru.rutcampustrack.academic.contract.dto.assistant;

import jakarta.validation.constraints.NotEmpty;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.util.List;

/**
 * Request DTO for updating the permissions of an existing headman assistant.
 */
public record UpdateAssistantPermissionsRequest(

        @NotEmpty(message = "Список прав обязателен")
        List<AssistantPermission> permissions
) {}
