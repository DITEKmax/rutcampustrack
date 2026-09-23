package ru.rutcampustrack.attendance.report.teacher;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, renderer-neutral input for teacher attendance exports.
 *
 * <p>Rows and columns identify the exact lesson/student records used to
 * produce this report. Metrics are calculated once by the server for this
 * same context and are only displayed by format renderers.</p>
 */
public record TeacherAttendanceExportModel(
        Context context,
        List<Column> columns,
        List<Row> rows
) {
    public TeacherAttendanceExportModel {
        context = Objects.requireNonNull(context, "context");
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        if (context.kind() == ReportKind.SUBJECT_JOURNAL
                && rows.stream().anyMatch(row -> row.metrics().isEmpty())) {
            throw new IllegalArgumentException("Subject-journal rows require server-supplied metrics");
        }
    }

    public enum ReportKind {
        WEEKLY_ATTENDANCE,
        SUBJECT_JOURNAL
    }

    public record Context(
            ReportKind kind,
            long semesterId,
            String semesterLabel,
            long groupId,
            String groupLabel,
            Long subjectId,
            String subjectLabel,
            LocalDate periodFrom,
            LocalDate periodTo,
            List<String> typeLabels,
            Instant generatedAt
    ) {
        public Context {
            kind = Objects.requireNonNull(kind, "kind");
            semesterLabel = Objects.requireNonNullElse(semesterLabel, "");
            groupLabel = Objects.requireNonNullElse(groupLabel, "");
            subjectLabel = Objects.requireNonNullElse(subjectLabel, "");
            periodFrom = Objects.requireNonNull(periodFrom, "periodFrom");
            periodTo = Objects.requireNonNull(periodTo, "periodTo");
            typeLabels = List.copyOf(Objects.requireNonNull(typeLabels, "typeLabels"));
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
        }
    }

    public record Column(
            long lessonId,
            LocalDate date,
            LocalTime startTime,
            int lessonNumber,
            long subjectId,
            String subjectLabel,
            String lessonType,
            String typeLabel,
            String state
    ) {
        public Column {
            date = Objects.requireNonNull(date, "date");
            subjectLabel = Objects.requireNonNullElse(subjectLabel, "");
            lessonType = Objects.requireNonNullElse(lessonType, "");
            typeLabel = Objects.requireNonNullElse(typeLabel, "");
            state = Objects.requireNonNullElse(state, "");
        }
    }

    public record Row(
            long studentId,
            String displayName,
            Map<Long, Cell> cellsByLessonId,
            Optional<Metrics> metrics
    ) {
        public Row {
            displayName = Objects.requireNonNullElse(displayName, "");
            cellsByLessonId = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(cellsByLessonId, "cellsByLessonId")));
            metrics = Objects.requireNonNull(metrics, "metrics");
        }
    }

    /**
     * A missing key in {@code cellsByLessonId} means there is no cell for that
     * student/lesson pair. It must not be interpreted as an absent mark.
     */
    public record Cell(
            String symbol,
            AttendanceStatus status,
            boolean recordPresent
    ) {
        public Cell {
            symbol = Objects.requireNonNullElse(symbol, "");
        }
    }

    /**
     * Counts, denominator, and percentages are supplied by the server for the
     * exact lesson selection and student membership in this report context.
     * Percentages are in the 0..100 range; renderers do not recompute them. When the denominator is zero, they show no data rather than 0%.
     * Weekly attendance matrices may omit these metrics when their layout does
     * not contain a summary section.
     */
    public record Metrics(
            int presentCount,
            int excusedCount,
            int absentCount,
            int presentOrExcusedCount,
            int denominator,
            BigDecimal percentPresent,
            BigDecimal percentPresentOrExcused,
            BigDecimal percentExcused,
            BigDecimal percentAbsent
    ) {
        public Metrics {
            percentPresent = Objects.requireNonNull(percentPresent, "percentPresent");
            percentPresentOrExcused = Objects.requireNonNull(percentPresentOrExcused, "percentPresentOrExcused");
            percentExcused = Objects.requireNonNull(percentExcused, "percentExcused");
            percentAbsent = Objects.requireNonNull(percentAbsent, "percentAbsent");
        }
    }
}
