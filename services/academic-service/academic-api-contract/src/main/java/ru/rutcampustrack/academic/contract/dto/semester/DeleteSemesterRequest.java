package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for deleting a semester. Requires a confirmation phrase to prevent
 * accidental deletion of active semesters (per D-12).
 */
@Schema(description = "Запрос на удаление семестра (требует фразу подтверждения, D-12)")
public record DeleteSemesterRequest(

        @Schema(description = "Фраза подтверждения (должна совпасть с названием семестра)",
                example = "Весенний 2026",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Подтверждение обязательно")
        String confirmation
) {}
