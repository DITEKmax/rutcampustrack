package ru.rutcampustrack.academic.contract.dto.homework;

import jakarta.validation.constraints.NotBlank;

public record UpdateHomeworkRequest(
        @NotBlank(message = "Заголовок домашнего задания обязателен")
        String title,

        String description,

        String link
) {}
