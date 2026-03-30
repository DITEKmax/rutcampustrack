package ru.rutcampustrack.academic.contract.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

public record UpdateSubjectRequest(
        @NotBlank(message = "Название предмета не может быть пустым")
        String name,

        @NotNull(message = "Тип предмета обязателен")
        SubjectType type
) {}
