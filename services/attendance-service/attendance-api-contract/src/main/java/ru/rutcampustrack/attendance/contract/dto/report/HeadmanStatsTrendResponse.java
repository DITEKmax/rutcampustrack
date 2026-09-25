package ru.rutcampustrack.attendance.contract.dto.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Weekly or daily attendance trend on one shared bucket grid for both metrics. */
public record HeadmanStatsTrendResponse(
        HeadmanStatsResponse.Context context,
        HeadmanStatsTrendQueryRequest.Mode mode,
        List<Point> points,
        EmptyState emptyState
) {
    public HeadmanStatsTrendResponse {
        context = Objects.requireNonNull(context, "context");
        mode = Objects.requireNonNull(mode, "mode");
        points = List.copyOf(Objects.requireNonNull(points, "points"));
        emptyState = Objects.requireNonNull(emptyState, "emptyState");
    }

    public record Point(
            String key,
            String label,
            LocalDate from,
            LocalDate to,
            TrendMetric present,
            TrendMetric presentOrExcused
    ) {
        public Point {
            key = Objects.requireNonNull(key, "key");
            label = Objects.requireNonNullElse(label, "");
            from = Objects.requireNonNull(from, "from");
            to = Objects.requireNonNull(to, "to");
            if (to.isBefore(from)) throw new IllegalArgumentException("Invalid trend point range");
            present = Objects.requireNonNull(present, "present");
            presentOrExcused = Objects.requireNonNull(presentOrExcused, "presentOrExcused");
        }
    }

    /** A null percent means no eligible lesson-student pairs exist in this bucket. */
    public record TrendMetric(int numerator, int denominator, Double percent) {
        public TrendMetric {
            if (numerator < 0 || denominator < 0 || numerator > denominator) {
                throw new IllegalArgumentException("Invalid trend metric counts");
            }
            if (denominator == 0 && (numerator != 0 || percent != null)) {
                throw new IllegalArgumentException("Empty trend metric must have a null percent");
            }
            if (denominator > 0 && (percent == null || !Double.isFinite(percent)
                    || percent < 0 || percent > 100)) {
                throw new IllegalArgumentException("Trend percent must be from 0 to 100");
            }
        }
    }

    public enum EmptyState {
        NONE,
        NO_ACTIVE_SEMESTER,
        NO_COMPLETED_LESSONS,
        NO_MATCHING_LESSONS
    }
}
