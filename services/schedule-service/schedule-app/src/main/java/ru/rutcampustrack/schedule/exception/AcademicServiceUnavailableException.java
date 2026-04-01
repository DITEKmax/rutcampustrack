package ru.rutcampustrack.schedule.exception;

/**
 * Thrown when Academic Service is unreachable or returns an unexpected gRPC error.
 * Mapped to HTTP 503 by GlobalExceptionHandler.
 */
public class AcademicServiceUnavailableException extends RuntimeException {

    public AcademicServiceUnavailableException(String message) {
        super(message);
    }
}
