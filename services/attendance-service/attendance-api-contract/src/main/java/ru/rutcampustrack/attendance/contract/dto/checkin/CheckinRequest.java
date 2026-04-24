package ru.rutcampustrack.attendance.contract.dto.checkin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for geo-checkin (D-06).
 * Java record — no Lombok per contract module rules.
 */
@Schema(description = "Запрос на геоотметку студента на паре")
public record CheckinRequest(

        @Schema(description = "Широта точки геоотметки (WGS84)",
                example = "55.755",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "-90")
        @DecimalMax(value = "90")
        Double lat,

        @Schema(description = "Долгота точки геоотметки (WGS84)",
                example = "37.617",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @DecimalMin(value = "-180")
        @DecimalMax(value = "180")
        Double lng
) {}
