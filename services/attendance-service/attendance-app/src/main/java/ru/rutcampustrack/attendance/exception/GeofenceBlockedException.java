package ru.rutcampustrack.attendance.exception;

/**
 * Thrown when geo-checkin is blocked for a lesson (e.g., geo_blocked=true flag).
 * GlobalExceptionHandler maps this to HTTP 403 Forbidden.
 */
public class GeofenceBlockedException extends RuntimeException {

    public GeofenceBlockedException(String message) {
        super(message);
    }
}
