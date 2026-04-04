package ru.rutcampustrack.attendance.contract.dto.checkin;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for geo-checkin (D-06).
 * Java record — no Lombok per contract module rules.
 */
public record CheckinRequest(

        @NotNull
        @DecimalMin(value = "-90")
        @DecimalMax(value = "90")
        Double lat,

        @NotNull
        @DecimalMin(value = "-180")
        @DecimalMax(value = "180")
        Double lng
) {}
