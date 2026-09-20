package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import jakarta.validation.Valid;
import java.util.List;

/**
 * Request DTO for creating a new subject (HEADMAN/ADMIN).
 *
 * <p>Группа берётся из {@code RequestContext}; assignments contain all
 * identity and validity fields and are created in the same transaction.
 */
@Schema(description = "Запрос на создание нового предмета (HEADMAN/ADMIN)")
public record CreateSubjectRequest(

        @Schema(description = "Название предмета", example = "Математика",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Название предмета обязательно")
        String name,

        @Schema(description = "Тип предмета (LECTURE / PRACTICE / LAB)",
                example = "LECTURE",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Тип предмета обязателен")
        SubjectType type,

        @Schema(description = "Канонические типы занятий (1-3)", example = "[\"LECTURE\", \"PRACTICE\"]")
        List<SubjectType> lessonTypes,

        @Schema(description = "Начальные назначения, создаваемые атомарно")
        List<@NotNull @Valid InitialAssignmentRequest> initialAssignments,

        @Schema(description = "Устаревший список преподавателей; непустой список отклоняется")
        List<Long> teacherIds
) {

    /** Source compatibility for the former {name,type,teacherIds} payload. */
    public CreateSubjectRequest(String name, SubjectType type, List<Long> teacherIds) {
        this(name, type, null, null, teacherIds);
    }

    /** Canonical convenience constructor without the legacy field. */
    public CreateSubjectRequest(String name,
                                SubjectType type,
                                List<SubjectType> lessonTypes,
                                List<InitialAssignmentRequest> initialAssignments) {
        this(name, type, lessonTypes, initialAssignments, null);
    }
}
