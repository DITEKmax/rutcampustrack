package ru.rutcampustrack.attendance.report.studentprojection;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.util.Objects;

/**
 * Signals that data cannot be used to build a student read projection.
 *
 * <p>The exception is deliberately independent from Spring and persistence so
 * that the projection calculators can be used by a future adapter without
 * changing their integrity boundary.</p>
 */
public final class StudentProjectionException extends RuntimeException {

    public enum Kind {
        INVALID_OCCURRENCE,
        DUPLICATE_OCCURRENCE,
        UNSUPPORTED_ATTENDANCE_STATUS,
        INVALID_ROSTER,
        INVALID_METRICS
    }

    private final Kind kind;

    public StudentProjectionException(Kind kind, String message) {
        super(Objects.requireNonNull(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public StudentProjectionException(Kind kind, String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.kind = Objects.requireNonNull(kind, "kind");
    }

    public Kind kind() {
        return kind;
    }

    /** Alias useful to adapters that expose a reason/code field. */
    public Kind reason() {
        return kind;
    }

    public static StudentProjectionException invalidOccurrence(String detail) {
        return new StudentProjectionException(
                Kind.INVALID_OCCURRENCE,
                "Invalid attendance occurrence: " + detail);
    }

    public static StudentProjectionException duplicateOccurrence(Long occurrenceId) {
        return new StudentProjectionException(
                Kind.DUPLICATE_OCCURRENCE,
                "Duplicate attendance occurrence id: " + occurrenceId);
    }

    public static StudentProjectionException unsupportedAttendanceStatus(
            Long occurrenceId,
            AttendanceStatus status) {
        return new StudentProjectionException(
                Kind.UNSUPPORTED_ATTENDANCE_STATUS,
                "Unsupported attendance status " + status
                        + " for occurrence " + occurrenceId);
    }

    public static StudentProjectionException invalidRoster(String detail) {
        return new StudentProjectionException(
                Kind.INVALID_ROSTER,
                "Invalid active participant roster: " + detail);
    }

    public static StudentProjectionException invalidMetrics(String detail) {
        return new StudentProjectionException(
                Kind.INVALID_METRICS,
                "Invalid attendance metrics: " + detail);
    }
}
