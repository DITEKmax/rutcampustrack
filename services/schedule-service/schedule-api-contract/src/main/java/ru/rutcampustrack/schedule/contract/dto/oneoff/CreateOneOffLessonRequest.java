package ru.rutcampustrack.schedule.contract.dto.oneoff;

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
public record CreateOneOffLessonRequest(

        @NotNull
        Long groupId,

        @NotNull
        Long subjectId,

        @NotNull
        LocalDate date,

        @NotNull @Min(1) @Max(8)
        Short lessonNumber,

        @Size(max = 64)
        String classroom
) {}
