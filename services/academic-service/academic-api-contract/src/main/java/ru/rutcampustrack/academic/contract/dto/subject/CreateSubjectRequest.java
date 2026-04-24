package ru.rutcampustrack.academic.contract.dto.subject;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.rutcampustrack.academic.contract.enums.SubjectType;

import java.util.List;

/**
 * Request DTO for creating a new subject (HEADMAN/ADMIN).
 *
 * <p>Phase 60-01 / D-02: староста указывает название, тип и список
 * преподавателей-наблюдателей (N штук, все равноправные, D-15). Группа
 * берётся из {@code RequestContext} (JWT claim), не из тела запроса.
 *
 * @param name       human-readable subject name
 * @param type       subject type (LECTURE / PRACTICE / LAB)
 * @param teacherIds идентификаторы преподавателей для текущего семестра;
 *                   может быть пустым списком, но не {@code null}.
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

        @Schema(description = "ID преподавателей-наблюдателей (может быть пустым, но не null)",
                example = "[42, 43]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Список преподавателей обязателен")
        List<Long> teacherIds
) {}
