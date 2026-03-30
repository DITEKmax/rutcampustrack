package ru.rutcampustrack.academic.contract.dto.homework;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for full replacement update of a homework (PUT semantics).
 */
public record UpdateHomeworkRequest(

        @NotBlank(message = "Название задания обязательно")
        String title,

        String description,

        String link
) {}
