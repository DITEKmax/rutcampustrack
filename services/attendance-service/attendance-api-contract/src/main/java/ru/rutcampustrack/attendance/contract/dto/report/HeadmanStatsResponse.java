package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Active-semester context, summaries and one server-paged student table response. */
public record HeadmanStatsResponse(
        Context context,
        Metrics summary,
        int filteredStudents,
        List<StudentRow> rows,
        List<SubjectOption> subjects,
        List<ColumnDescriptor> columns,
        List<FormatOption> formats,
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasPrevious,
        boolean hasNext,
        EmptyState emptyState
) {
    public HeadmanStatsResponse {
        context = Objects.requireNonNull(context, "context");
        summary = Objects.requireNonNull(summary, "summary");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        columns = List.copyOf(Objects.requireNonNull(columns, "columns"));
        formats = List.copyOf(Objects.requireNonNull(formats, "formats"));
        emptyState = Objects.requireNonNull(emptyState, "emptyState");
    }

    public record Context(
            long groupId,
            String groupName,
            Long semesterId,
            String semesterName,
            LocalDate semesterFrom,
            LocalDate semesterTo,
            Long subjectId,
            String subjectName,
            List<String> lessonTypes,
            int lessonsCount,
            Instant generatedAt
    ) {
        public Context {
            if (groupId <= 0 || lessonsCount < 0) throw new IllegalArgumentException("Invalid statistics context");
            groupName = Objects.requireNonNullElse(groupName, "");
            semesterName = Objects.requireNonNullElse(semesterName, "");
            subjectName = Objects.requireNonNullElse(subjectName, "");
            lessonTypes = List.copyOf(Objects.requireNonNull(lessonTypes, "lessonTypes"));
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            if ((semesterFrom == null) != (semesterTo == null)
                    || semesterFrom != null && semesterTo.isBefore(semesterFrom)) {
                throw new IllegalArgumentException("Invalid semester range");
            }
        }
    }

    public record Metrics(
            Metric present,
            Metric presentOrExcused,
            Metric excused,
            Metric absent
    ) {
        public Metrics {
            present = Objects.requireNonNull(present, "present");
            presentOrExcused = Objects.requireNonNull(presentOrExcused, "presentOrExcused");
            excused = Objects.requireNonNull(excused, "excused");
            absent = Objects.requireNonNull(absent, "absent");
        }
    }

    public record Metric(int numerator, int denominator, double percent) {
        public Metric {
            if (numerator < 0 || denominator < 0 || numerator > denominator
                    || !Double.isFinite(percent) || percent < 0 || percent > 100) {
                throw new IllegalArgumentException("Invalid statistics metric");
            }
        }
    }

    public record TicketCounts(int submitted, int approved, int rejected) {
        public TicketCounts {
            if (submitted < 0 || approved < 0 || rejected < 0) throw new IllegalArgumentException("Invalid ticket count");
        }
    }

    public record Sources(int studentGeo, int manualRequest, int autoAfterGeoFailure, int headmanManual) {
        public Sources {
            if (studentGeo < 0 || manualRequest < 0 || autoAfterGeoFailure < 0 || headmanManual < 0) {
                throw new IllegalArgumentException("Invalid attendance source count");
            }
        }
    }

    public record StudentRow(
            long studentId,
            String displayName,
            Metrics metrics,
            TicketCounts lateCheckin,
            TicketCounts excuse,
            Sources sources
    ) {
        public StudentRow {
            if (studentId <= 0) throw new IllegalArgumentException("Invalid student identity");
            displayName = Objects.requireNonNullElse(displayName, "");
            metrics = Objects.requireNonNull(metrics, "metrics");
            lateCheckin = Objects.requireNonNull(lateCheckin, "lateCheckin");
            excuse = Objects.requireNonNull(excuse, "excuse");
            sources = Objects.requireNonNull(sources, "sources");
        }
    }

    public record LessonTypeOption(String code, String label) {
        public LessonTypeOption {
            code = Objects.requireNonNullElse(code, "");
            label = Objects.requireNonNullElse(label, code);
        }
    }

    public record SubjectOption(long id, String label, List<LessonTypeOption> lessonTypes) {
        public SubjectOption {
            if (id <= 0) throw new IllegalArgumentException("Invalid subject identity");
            label = Objects.requireNonNullElse(label, "");
            lessonTypes = List.copyOf(Objects.requireNonNull(lessonTypes, "lessonTypes"));
        }
    }

    public enum FilterKind {
        TEXT,
        RANGE
    }

    public record ColumnDescriptor(String field, String label, FilterKind filterKind) {
        public ColumnDescriptor {
            field = Objects.requireNonNull(field, "field");
            label = Objects.requireNonNullElse(label, field);
            filterKind = Objects.requireNonNull(filterKind, "filterKind");
        }
    }

    public record FormatOption(String code, String label, String contentType, String extension) {
        public FormatOption {
            code = Objects.requireNonNull(code, "code");
            label = Objects.requireNonNullElse(label, code);
            contentType = Objects.requireNonNull(contentType, "contentType");
            extension = Objects.requireNonNull(extension, "extension");
        }
    }

    public enum EmptyState {
        NONE,
        NO_ACTIVE_SEMESTER,
        NO_COMPLETED_LESSONS,
        NO_MEMBERS,
        FILTERED_EMPTY
    }
}
