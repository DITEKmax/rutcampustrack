package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Context;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.StudentRow;

import java.util.List;
import java.util.Objects;

/** Fully filtered and sorted rows for one headman statistics export block. */
record HeadmanStatsExportModel(Context context, Metrics summary, int filteredStudents, List<StudentRow> rows,
                               String filters, String sorts) {
    HeadmanStatsExportModel {
        context = Objects.requireNonNull(context, "context");
        summary = Objects.requireNonNull(summary, "summary");
        if (filteredStudents < 0) throw new IllegalArgumentException("Invalid filtered student count");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        filters = Objects.requireNonNull(filters, "filters");
        sorts = Objects.requireNonNull(sorts, "sorts");
    }
}
