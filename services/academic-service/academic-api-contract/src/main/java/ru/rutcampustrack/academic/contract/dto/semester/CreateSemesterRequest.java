package ru.rutcampustrack.academic.contract.dto.semester;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for creating a new academic semester.
 */
public record CreateSemesterRequest(

        @NotBlank(message = "Название семестра обязательно")
        String name,

        @NotNull(message = "Дата начала обязательна")
        LocalDate dateFrom,

        @NotNull(message = "Дата окончания обязательна")
        LocalDate dateTo
) {}
