package ru.rutcampustrack.academic.exception;

/**
 * Thrown when the authenticated user does not have the required role.
 * Mapped to HTTP 403 by {@link GlobalExceptionHandler}.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
