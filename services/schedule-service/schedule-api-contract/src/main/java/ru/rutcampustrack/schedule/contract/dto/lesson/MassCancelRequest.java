package ru.rutcampustrack.schedule.contract.dto.lesson;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for mass-cancelling lessons for a group over a date range.
 * No Lombok — contract modules use plain Java records.
 */
public record MassCancelRequest(

        @NotNull
        Long groupId,

        @NotNull
        LocalDate dateFrom,

        @NotNull
        LocalDate dateTo,

        @NotBlank @Size(max = 512)
        String reason
) {}
