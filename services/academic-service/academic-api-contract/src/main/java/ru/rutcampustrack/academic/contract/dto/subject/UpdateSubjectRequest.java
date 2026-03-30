package ru.rutcampustrack.academic.contract.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/**
 * Request DTO for full replacement update of a subject (PUT semantics).
 */
public record UpdateSubjectRequest(

        @NotBlank(message = "Название предмета обязательно")
        String name,

        @NotNull(message = "Тип предмета обязателен")
        SubjectType type
) {}
