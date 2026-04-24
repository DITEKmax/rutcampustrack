package ru.rutcampustrack.academic.contract.dto.assistant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;

import java.util.List;

/**
 * Request DTO for assigning a headman assistant with initial permissions.
 */
@Schema(description = "Запрос на назначение помощника старосты с начальным набором прав")
public record AssignAssistantRequest(

        @Schema(description = "ID студента, назначаемого помощником", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID студента обязателен")
        Long studentId,

        @Schema(description = "ID группы, в которой действуют права", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @Schema(description = "Набор делегируемых прав (минимум один элемент)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "Список прав обязателен")
        List<AssistantPermission> permissions
) {}
