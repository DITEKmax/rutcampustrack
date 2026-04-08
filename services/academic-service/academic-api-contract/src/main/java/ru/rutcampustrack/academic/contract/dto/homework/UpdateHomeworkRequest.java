package ru.rutcampustrack.academic.contract.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for full replacement update of a homework (PUT semantics).
 */
public record UpdateHomeworkRequest(

        @NotBlank(message = "Название задания обязательно")
        @Size(max = 255)
        String title,

        @Size(max = 4000)
        String description,

        @Size(max = 2048)
        String link
) {}
