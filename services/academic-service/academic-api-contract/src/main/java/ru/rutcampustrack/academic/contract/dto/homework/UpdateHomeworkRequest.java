package ru.rutcampustrack.academic.contract.dto.homework;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for full replacement update of a homework (PUT semantics).
 */
@Schema(description = "Запрос на полное обновление домашнего задания (PUT)")
public record UpdateHomeworkRequest(

        @Schema(description = "Название задания",
                example = "Интегралы 1-5",
                requiredMode = Schema.RequiredMode.REQUIRED,
                maxLength = 255)
        @NotBlank(message = "Название задания обязательно")
        @Size(max = 255)
        String title,

        @Schema(description = "Описание задания (опционально)",
                example = "Решить задачи 1-5 из учебника Демидовича",
                maxLength = 4000)
        @Size(max = 4000)
        String description,

        @Schema(description = "Ссылка на материалы (опционально)",
                example = "https://example.com/homework.pdf",
                maxLength = 2048)
        @Size(max = 2048)
        String link
) {}
