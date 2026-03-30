package ru.rutcampustrack.academic.contract.dto.threshold;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SetThresholdRequest(
        @NotNull(message = "Процент посещаемости обязателен")
        @Min(value = 0, message = "Минимальный порог — 0%")
        @Max(value = 100, message = "Максимальный порог — 100%")
        Integer minPercentage
) {}
