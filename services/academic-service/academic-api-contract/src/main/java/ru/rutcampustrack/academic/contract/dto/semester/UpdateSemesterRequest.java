package ru.rutcampustrack.academic.contract.dto.semester;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UpdateSemesterRequest(
        @NotBlank(message = "Название семестра не может быть пустым")
        String name,

        @NotNull(message = "Дата начала семестра обязательна")
        LocalDate dateFrom,

        @NotNull(message = "Дата окончания семестра обязательна")
        LocalDate dateTo
) {}
