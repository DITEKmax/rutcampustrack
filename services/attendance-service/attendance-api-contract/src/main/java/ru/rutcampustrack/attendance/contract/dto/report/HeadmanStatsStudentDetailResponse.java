package ru.rutcampustrack.attendance.contract.dto.report;

import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Personal semester detail; ticket bodies, causes and attachments are intentionally absent. */
public record HeadmanStatsStudentDetailResponse(
        HeadmanStatsResponse.Context context,
        Student student,
        Metrics metrics,
        List<SubjectMetrics> subjects,
        List<WeekMetrics> weeks,
        TicketPage<LateCheckinTicket> lateCheckins,
        TicketPage<ExcuseTicketEntry> excuses,
        EmptyState emptyState
) {
    public HeadmanStatsStudentDetailResponse {
        context = Objects.requireNonNull(context, "context");
        student = Objects.requireNonNull(student, "student");
        metrics = Objects.requireNonNull(metrics, "metrics");
        subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        weeks = List.copyOf(Objects.requireNonNull(weeks, "weeks"));
        lateCheckins = Objects.requireNonNull(lateCheckins, "lateCheckins");
        excuses = Objects.requireNonNull(excuses, "excuses");
        emptyState = Objects.requireNonNull(emptyState, "emptyState");
    }

    public record Student(long id, String displayName) {
        public Student {
            if (id <= 0) throw new IllegalArgumentException("Invalid student identity");
            displayName = Objects.requireNonNullElse(displayName, "");
        }
    }

    public record SubjectMetrics(long subjectId, String subjectName, Metrics metrics,
                                 List<LessonTypeMetrics> lessonTypes) {
        public SubjectMetrics {
            if (subjectId <= 0) throw new IllegalArgumentException("Invalid subject identity");
            subjectName = Objects.requireNonNullElse(subjectName, "");
            metrics = Objects.requireNonNull(metrics, "metrics");
            lessonTypes = List.copyOf(Objects.requireNonNull(lessonTypes, "lessonTypes"));
        }
    }

    public record LessonTypeMetrics(String code, String label, Metrics metrics) {
        public LessonTypeMetrics {
            code = Objects.requireNonNullElse(code, "");
            label = Objects.requireNonNullElse(label, code);
            metrics = Objects.requireNonNull(metrics, "metrics");
        }
    }

    public record WeekMetrics(LocalDate weekStart, LocalDate from, LocalDate to,
                              HeadmanStatsTrendResponse.TrendMetric present,
                              HeadmanStatsTrendResponse.TrendMetric presentOrExcused) {
        public WeekMetrics {
            weekStart = Objects.requireNonNull(weekStart, "weekStart");
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            if (to.isBefore(from)) throw new IllegalArgumentException("Invalid personal trend week");
            present = Objects.requireNonNull(present, "present");
            presentOrExcused = Objects.requireNonNull(presentOrExcused, "presentOrExcused");
        }
    }

    public record TicketPage<T>(int page, int size, long totalElements, int totalPages,
                                boolean hasPrevious, boolean hasNext, List<T> items) {
        public TicketPage {
            if (page < 0 || size < 1 || totalElements < 0 || totalPages < 0) {
                throw new IllegalArgumentException("Invalid ticket page");
            }
            items = List.copyOf(Objects.requireNonNull(items, "items"));
        }
    }

    public record LateCheckinTicket(String id, LocalDate lessonDate, Long subjectId,
                                    String subjectName, String lessonType, Integer lessonNumber,
                                    Instant submittedAt, Instant decidedAt,
                                    LateCheckinRequestStatus status, LateCheckinRequestOrigin origin) {
        public LateCheckinTicket {
            id = Objects.requireNonNull(id, "id");
            lessonDate = Objects.requireNonNull(lessonDate, "lessonDate");
            subjectName = Objects.requireNonNullElse(subjectName, "");
            lessonType = Objects.requireNonNullElse(lessonType, "");
            status = Objects.requireNonNull(status, "status");
            origin = Objects.requireNonNull(origin, "origin");
        }
    }

    public record ExcuseTicketEntry(String id, Instant submittedAt, Instant decidedAt,
                                    ExcuseTicketStatus status, List<TicketLesson> lessons) {
        public ExcuseTicketEntry {
            id = Objects.requireNonNull(id, "id");
            status = Objects.requireNonNull(status, "status");
            lessons = List.copyOf(Objects.requireNonNull(lessons, "lessons"));
        }
    }

    public record TicketLesson(long lessonId, LocalDate lessonDate, String subjectName,
                               String lessonType, Integer lessonNumber) {
        public TicketLesson {
            if (lessonId <= 0) throw new IllegalArgumentException("Invalid lesson identity");
            lessonDate = Objects.requireNonNull(lessonDate, "lessonDate");
            subjectName = Objects.requireNonNullElse(subjectName, "");
            lessonType = Objects.requireNonNullElse(lessonType, "");
        }
    }

    public enum EmptyState {
        NONE,
        NO_ACTIVE_SEMESTER,
        NO_COMPLETED_LESSONS
    }
}
