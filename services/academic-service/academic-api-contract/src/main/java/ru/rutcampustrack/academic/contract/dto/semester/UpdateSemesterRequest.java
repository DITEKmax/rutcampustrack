package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for full replacement update of a semester (PUT semantics).
 */
@Schema(description = "Запрос на полное обновление семестра (PUT)")
public record UpdateSemesterRequest(

        @Schema(description = "Название семестра", example = "Весенний 2026",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Название семестра обязательно")
        String name,

        @Schema(description = "Дата начала семестра", example = "2026-02-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дата начала обязательна")
        LocalDate dateFrom,

        @Schema(description = "Дата окончания семестра", example = "2026-06-30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дата окончания обязательна")
        LocalDate dateTo
) {}
