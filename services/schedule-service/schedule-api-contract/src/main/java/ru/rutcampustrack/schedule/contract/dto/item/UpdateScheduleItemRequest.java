package ru.rutcampustrack.schedule.contract.dto.item;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.rutcampustrack.schedule.contract.enums.WeekType;

import java.time.LocalTime;

/**
 * Request DTO for full update (PUT) of a schedule template item.
 * Excludes groupId and semesterId — these are immutable after creation (D-09).
 * No Lombok — contract modules use plain Java records.
 */
public record UpdateScheduleItemRequest(

        @NotNull
        Long subjectId,

        @NotNull
        Long teacherId,

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
