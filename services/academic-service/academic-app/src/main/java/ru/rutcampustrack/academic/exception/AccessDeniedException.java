package ru.rutcampustrack.academic.exception;

/**
 * Thrown when the authenticated user lacks the required role or permission.
 * GlobalExceptionHandler maps this to 403 Forbidden.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
