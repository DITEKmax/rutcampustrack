package ru.rutcampustrack.schedule.exception;

/**
 * Thrown when an operation conflicts with the current state of a resource.
 * Mapped to HTTP 409 Conflict by GlobalExceptionHandler.
 *
 * Usage examples:
 *  - D-09: one-off lesson creation while an active template slot is occupying (group, date, slot).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
