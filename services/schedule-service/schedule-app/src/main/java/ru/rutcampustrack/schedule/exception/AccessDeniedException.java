package ru.rutcampustrack.schedule.exception;

/**
 * Thrown when the authenticated user does not have the required role.
 * Mapped to HTTP 403 by GlobalExceptionHandler.
 *
 * This is NOT org.springframework.security.access.AccessDeniedException —
 * it is a custom exception matching the project pattern (no Spring Security dependency).
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
