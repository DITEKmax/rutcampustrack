package ru.rutcampustrack.schedule.contract.dto.item;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalTime;

/**
 * Request DTO for creating a schedule template item.
 * No Lombok — contract modules use plain Java records.
 * <p>
 * D-16: teacherId removed. Teacher access to journals is resolved via JOIN
 * ScheduleItem × TeacherSubjectGroup, not by slot-level teacher assignment.
 */
@Schema(description = "Запрос на создание шаблона пары в расписании (повторяющийся слот)")
public record CreateScheduleItemRequest(

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

        @Schema(description = "ID семестра",
                example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long semesterId,

        @Schema(description = "День недели: 1 — понедельник, 7 — воскресенье",
                example = "1",
                minimum = "1", maximum = "7",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(7)
        Short dayOfWeek,

        @Schema(description = "Номер пары в дне (1..8)",
                example = "2",
                minimum = "1", maximum = "8",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) @Max(8)
        Short lessonNumber,

        @Schema(description = "Время начала пары",
                example = "10:30:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalTime startTime,

        @Schema(description = "Время окончания пары",
                example = "12:00:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalTime endTime,

        @Schema(description = "Тип недели: ODD — 1-я (ISO чётная), EVEN — 2-я (ISO нечётная)",
                example = "ODD",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        WeekType weekType,

        @Schema(description = "Номер аудитории",
                example = "3-405",
                maxLength = 64)
        @Size(max = 64)
        String room
) {}
