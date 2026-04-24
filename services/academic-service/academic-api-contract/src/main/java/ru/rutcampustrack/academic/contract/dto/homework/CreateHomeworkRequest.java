package ru.rutcampustrack.academic.contract.dto.homework;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating a new homework assignment.
 *
 * <p>Phase 61 / D-01, D-03: {@code lessonDate} + {@code lessonNumber} образуют natural key пары,
 * к которой привязывается ДЗ. {@code @FutureOrPresent} сверяется по {@link java.time.Clock} bean,
 * но быстрая Bean Validation-проверка добавляет ещё один слой защиты (pitfall: при массовых
 * неправильных заявках не доходим до БД / gRPC).
 */
@Schema(description = "Запрос на создание нового домашнего задания (HEADMAN/ASSISTANT)")
public record CreateHomeworkRequest(

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
        String link,

        @Schema(description = "ID предмета", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID предмета обязателен")
        Long subjectId,

        @Schema(description = "ID группы", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @Schema(description = "ID семестра", example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID семестра обязателен")
        Long semesterId,

        @Schema(description = "Дата пары (не в прошлом)", example = "2026-04-24",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Дата пары обязательна")
        @FutureOrPresent(message = "Дата пары должна быть не в прошлом")
        LocalDate lessonDate,

        @Schema(description = "Номер пары (1..8)", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED,
                minimum = "1", maximum = "8")
        @NotNull(message = "Номер пары обязателен")
        @Min(value = 1, message = "Номер пары должен быть в диапазоне 1..8")
        @Max(value = 8, message = "Номер пары должен быть в диапазоне 1..8")
        Integer lessonNumber
) {}
