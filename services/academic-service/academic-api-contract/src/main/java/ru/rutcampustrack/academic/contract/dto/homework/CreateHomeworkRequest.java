package ru.rutcampustrack.academic.contract.dto.homework;

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
public record CreateHomeworkRequest(

        @NotBlank(message = "Название задания обязательно")
        @Size(max = 255)
        String title,

        @Size(max = 4000)
        String description,

        @Size(max = 2048)
        String link,

        @NotNull(message = "ID предмета обязателен")
        Long subjectId,

        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @NotNull(message = "ID семестра обязателен")
        Long semesterId,

        @NotNull(message = "Дата пары обязательна")
        @FutureOrPresent(message = "Дата пары должна быть не в прошлом")
        LocalDate lessonDate,

        @NotNull(message = "Номер пары обязателен")
        @Min(value = 1, message = "Номер пары должен быть в диапазоне 1..8")
        @Max(value = 8, message = "Номер пары должен быть в диапазоне 1..8")
        Integer lessonNumber
) {}
