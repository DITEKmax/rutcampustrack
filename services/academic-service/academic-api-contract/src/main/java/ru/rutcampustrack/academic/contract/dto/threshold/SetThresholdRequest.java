package ru.rutcampustrack.academic.contract.dto.threshold;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for setting an attendance threshold percentage.
 * Used for global, group-level, and subject-level thresholds.
 */
@Schema(description = "Запрос на установку порога посещаемости (0..100%); используется для global/group/subject")
public record SetThresholdRequest(

        @Schema(description = "Минимальный процент посещаемости (0..100)",
                example = "75",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "0", maximum = "100")
        @NotNull(message = "Минимальный процент обязателен")
        @Min(value = 0, message = "Процент не может быть меньше 0")
        @Max(value = 100, message = "Процент не может быть больше 100")
        Integer minPercentage
) {}
