package ru.rutcampustrack.academic.contract.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Objects;

/** Signed absolute changes from the end of the previous Moscow calendar day. */
@Schema(description = "Суточный прирост карточек масштаба к концу вчерашнего дня Europe/Moscow")
public record DashboardDailyGrowthResponse(
        Status status,
        LocalDate baselineDate,
        @Schema(
                        types = {"integer", "null"},
                        format = "int64")
                Long studentsDelta,
        @Schema(
                        types = {"integer", "null"},
                        format = "int64")
                Long teachersDelta,
        @Schema(
                        types = {"integer", "null"},
                        format = "int64")
                Long groupsDelta) {
    public enum Status {
        AVAILABLE,
        UNKNOWN_HISTORY
    }

    public DashboardDailyGrowthResponse {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(baselineDate, "baselineDate");
        boolean allKnown = studentsDelta != null && teachersDelta != null && groupsDelta != null;
        boolean allUnknown = studentsDelta == null && teachersDelta == null && groupsDelta == null;
        if (status == Status.AVAILABLE ? !allKnown : !allUnknown) {
            throw new IllegalArgumentException(
                    "Daily growth values must match their history status");
        }
    }

    public static DashboardDailyGrowthResponse unknown(LocalDate baselineDate) {
        return new DashboardDailyGrowthResponse(
                Status.UNKNOWN_HISTORY, baselineDate, null, null, null);
    }
}
