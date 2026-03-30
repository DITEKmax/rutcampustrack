package ru.rutcampustrack.academic.contract.dto.threshold;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for setting an attendance threshold percentage.
 * Used for global, group-level, and subject-level thresholds.
 */
public record SetThresholdRequest(

        @NotNull(message = "Минимальный процент обязателен")
        @Min(value = 0, message = "Процент не может быть меньше 0")
        @Max(value = 100, message = "Процент не может быть больше 100")
        Integer minPercentage
) {}
