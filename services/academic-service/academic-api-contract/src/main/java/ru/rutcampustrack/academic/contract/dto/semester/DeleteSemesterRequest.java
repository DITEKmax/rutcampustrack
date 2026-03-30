package ru.rutcampustrack.academic.contract.dto.semester;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for deleting a semester. Requires a confirmation phrase to prevent
 * accidental deletion of active semesters (per D-12).
 */
public record DeleteSemesterRequest(

        @NotBlank(message = "Подтверждение обязательно")
        String confirmation
) {}
