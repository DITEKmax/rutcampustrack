package ru.rutcampustrack.academic.contract.dto.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

/** Request DTO for updating the permissions of an existing headman assistant. */
@Schema(description = "Запрос на обновление прав помощника старосты")
public record UpdateAssistantPermissionsRequest(
        @Schema(
                        description = "Новый набор прав (минимум один элемент)",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotEmpty(message = "Список прав обязателен")
                List<AssistantPermission> permissions) {}
