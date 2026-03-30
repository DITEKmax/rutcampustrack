package ru.rutcampustrack.academic.exception;

/**
 * Thrown when a create/update operation conflicts with existing data.
 * GlobalExceptionHandler maps this to 409 Conflict.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
