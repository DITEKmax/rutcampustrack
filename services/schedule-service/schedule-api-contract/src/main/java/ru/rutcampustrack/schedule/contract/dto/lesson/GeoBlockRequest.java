package ru.rutcampustrack.schedule.contract.dto.lesson;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling geo-blocking on a lesson.
 * No Lombok — contract modules use plain Java records.
 */
public record GeoBlockRequest(

        @NotNull
        Boolean blocked
) {}
