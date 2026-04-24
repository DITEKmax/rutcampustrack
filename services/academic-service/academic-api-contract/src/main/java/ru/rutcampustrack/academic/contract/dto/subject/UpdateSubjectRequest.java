package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

/**
 * Request DTO for full replacement update of a subject (PUT semantics).
 */
@Schema(description = "Запрос на полное обновление предмета (PUT)")
public record UpdateSubjectRequest(

        @Schema(description = "Название предмета", example = "Математика",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Название предмета обязательно")
        String name,

        @Schema(description = "Тип предмета (LECTURE / PRACTICE / LAB)",
                example = "LECTURE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Тип предмета обязателен")
        SubjectType type
) {}
