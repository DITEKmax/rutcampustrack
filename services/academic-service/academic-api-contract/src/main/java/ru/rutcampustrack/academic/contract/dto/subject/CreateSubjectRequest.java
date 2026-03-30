package ru.rutcampustrack.academic.contract.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/**
 * Request DTO for creating a new subject (HEADMAN only).
 */
public record CreateSubjectRequest(

        @NotBlank(message = "Название предмета обязательно")
        String name,

        @NotNull(message = "Тип предмета обязателен")
        SubjectType type
) {}
