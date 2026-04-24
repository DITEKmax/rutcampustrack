package ru.rutcampustrack.academic.contract.dto.assignment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a teacher to a subject-group-semester combination.
 */
@Schema(description = "Запрос на назначение преподавателя на предмет в группе и семестре")
public record AssignTeacherRequest(

        @Schema(description = "Табельный номер преподавателя", example = "EMP-00123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Табельный номер преподавателя обязателен")
        String employeeNumber,

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
        Long semesterId
) {}
