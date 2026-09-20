package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.util.List;

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
        SubjectType type,

        @Schema(description = "Канонические типы занятий; отсутствие означает singleton type")
        List<SubjectType> lessonTypes
) {

    /** Source compatibility for the former {name,type} payload. */
    public UpdateSubjectRequest(String name, SubjectType type) {
        this(name, type, null);
    }
}
