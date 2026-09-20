package ru.rutcampustrack.attendance.exception;

/**
 * Signals that the legacy REST geo-checkin write path has been retired.
 *
 * <p>The canonical student check-in command is the only supported geo write
 * path.  Keeping this failure in the attendance service makes direct callers
 * fail closed before any legacy rate-limit, schedule, deduplication, Mongo or
 * event operation can run.</p>
 */
public class LegacyCheckinRetiredException extends RuntimeException {

    public LegacyCheckinRetiredException() {
        super("Используйте канонический student check-in API");
    }
}
