package ru.rutcampustrack.schedule.contract.dto.item;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalTime;

/**
 * Request DTO for creating a schedule template item.
 * No Lombok — contract modules use plain Java records.
 */
public record CreateScheduleItemRequest(

        @NotNull
        Long groupId,

        @NotNull
        Long subjectId,

        @NotNull
        Long teacherId,

        @NotNull
        Long semesterId,

        @NotNull @Min(1) @Max(7)
        Short dayOfWeek,

        @NotNull @Min(1) @Max(8)
        Short lessonNumber,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime,

        @NotNull
        WeekType weekType,

        @Size(max = 64)
        String room
) {}
