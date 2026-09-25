package ru.rutcampustrack.attendance.report.teacher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Immutable, server-composed rows for a teacher 123 statistics export. */
public record TeacherStatsExportModel(Context context, List<Row> rows) {
    public TeacherStatsExportModel {
        context = Objects.requireNonNull(context, "context");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (context.scope() == Scope.STUDENTS && rows.stream().anyMatch(row -> row.lessonsCount() != null)
                || context.scope() == Scope.GROUPS && rows.stream().anyMatch(row -> row.lessonsCount() == null)) {
            throw new IllegalArgumentException("Row shape does not match statistics scope");
        }
    }

    public enum Scope {
        STUDENTS,
        GROUPS
    }

    public record Context(
            Scope scope,
            long semesterId,
            LocalDate semesterFrom,
            LocalDate semesterTo,
            LocalDate dataFrom,
            LocalDate dataTo,
            List<String> groupLabels,
            String subjectLabel,
            List<String> typeLabels,
            int lessonsCount,
            Instant generatedAt
    ) {
        public Context {
            scope = Objects.requireNonNull(scope, "scope");
            if (semesterId <= 0 || lessonsCount < 0) throw new IllegalArgumentException("Invalid statistics context");
            semesterFrom = Objects.requireNonNull(semesterFrom, "semesterFrom");
            semesterTo = Objects.requireNonNull(semesterTo, "semesterTo");
            if (semesterTo.isBefore(semesterFrom)) throw new IllegalArgumentException("Invalid semester range");
            if ((dataFrom == null) != (dataTo == null)
                    || dataFrom != null && dataTo.isBefore(dataFrom)) {
                throw new IllegalArgumentException("Invalid data range");
            }
            groupLabels = List.copyOf(Objects.requireNonNull(groupLabels, "groupLabels"));
            subjectLabel = Objects.requireNonNullElse(subjectLabel, "");
            typeLabels = List.copyOf(Objects.requireNonNull(typeLabels, "typeLabels"));
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            if (scope == Scope.STUDENTS && (groupLabels.size() != 1 || subjectLabel.isBlank())) {
                throw new IllegalArgumentException("Student statistics require one group and a subject");
            }
        }
    }

    /** Rows and metrics are already filtered, sorted and calculated by ReportService. */
    public record Row(
            String label,
            Metric present,
            Metric presentOrExcused,
            Metric excused,
            Metric absent,
            Integer lessonsCount
    ) {
        public Row {
            label = Objects.requireNonNullElse(label, "");
            present = Objects.requireNonNull(present, "present");
            presentOrExcused = Objects.requireNonNull(presentOrExcused, "presentOrExcused");
            excused = Objects.requireNonNull(excused, "excused");
            absent = Objects.requireNonNull(absent, "absent");
            if (lessonsCount != null && lessonsCount < 0) throw new IllegalArgumentException("Invalid lessonsCount");
        }

        public List<Metric> metricsInDisplayOrder() {
            return List.of(present, presentOrExcused, excused, absent);
        }
    }

    public record Metric(int numerator, int denominator, BigDecimal percent) {
        public Metric {
            if (numerator < 0 || denominator < 0 || numerator > denominator) {
                throw new IllegalArgumentException("Invalid statistics metric");
            }
            percent = Objects.requireNonNull(percent, "percent");
        }
    }
}
