package ru.rutcampustrack.schedule.contract.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for cancelling a single lesson.
 * No Lombok — contract modules use plain Java records.
 */
public record CancelLessonRequest(

        @NotBlank @Size(max = 512)
        String reason
) {}
