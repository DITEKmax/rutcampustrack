package ru.rutcampustrack.academic.contract.dto.semester;

import jakarta.validation.constraints.NotBlank;

public record DeleteSemesterRequest(
        @NotBlank(message = "Подтверждение удаления обязательно")
        String confirmation
) {}
