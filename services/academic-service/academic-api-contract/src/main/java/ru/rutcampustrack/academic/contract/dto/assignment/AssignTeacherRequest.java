package ru.rutcampustrack.academic.contract.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a teacher to a subject-group-semester combination.
 */
public record AssignTeacherRequest(

        @NotBlank(message = "Табельный номер преподавателя обязателен")
        String employeeNumber,

        @NotNull(message = "ID предмета обязателен")
        Long subjectId,

        @NotNull(message = "ID группы обязателен")
        Long groupId,

        @NotNull(message = "ID семестра обязателен")
        Long semesterId
) {}
