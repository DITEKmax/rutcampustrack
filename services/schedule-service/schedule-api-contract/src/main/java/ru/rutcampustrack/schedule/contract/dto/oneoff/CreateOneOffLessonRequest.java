package ru.rutcampustrack.schedule.contract.dto.oneoff;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating a one-off lesson (Phase 60-03, D-04).
 * No Lombok — contract modules use plain Java records (project convention).
 *
 * semester_id is resolved server-side by the {@code date} field (D-23), so it is
 * intentionally absent from the request payload.
 */
@Schema(description = "Запрос на создание разовой пары вне шаблона расписания (semesterId определяется по дате на сервере, D-23)")
public record CreateOneOffLessonRequest(

        @Schema(description = "ID группы",
                example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long groupId,

        @Schema(description = "ID предмета",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long subjectId,

        @Schema(description = "Дата проведения разовой пары",
                example = "2026-04-24",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate date,

        @Schema(description = "Номер пары в дне (1..8)",
                example = "3",
                minimum = "1", maximum = "8",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(8)
        Short lessonNumber,

        @Schema(description = "Номер аудитории",
                example = "3-405",
                maxLength = 64)
        @Size(max = 64)
        String classroom
) {}
