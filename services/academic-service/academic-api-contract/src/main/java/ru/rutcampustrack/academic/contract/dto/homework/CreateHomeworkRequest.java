package ru.rutcampustrack.academic.contract.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new homework assignment.
 */
public record CreateHomeworkRequest(

        @NotBlank(message = "Название задания обязательно")
        String title,

        String description,

        String link,

        @NotNull(message = "ID предмета обязателен")
        Long subjectId,

        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @NotNull(message = "ID семестра обязателен")
        Long semesterId
) {}
