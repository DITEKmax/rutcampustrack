package ru.rutcampustrack.academic.contract.dto.homework;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new homework assignment.
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
        Long semesterId
) {}
