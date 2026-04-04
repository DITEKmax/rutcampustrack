package ru.rutcampustrack.attendance.exception;

/**
 * Thrown when student coordinates are outside campus geofence.
 * GlobalExceptionHandler maps this to HTTP 422 Unprocessable Entity.
 */
public class GeofenceViolationException extends RuntimeException {

    public GeofenceViolationException(String message) {
        super(message);
    }
}
