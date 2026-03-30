package ru.rutcampustrack.academic.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (e.g. duplicate login, group already has a headman).
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
