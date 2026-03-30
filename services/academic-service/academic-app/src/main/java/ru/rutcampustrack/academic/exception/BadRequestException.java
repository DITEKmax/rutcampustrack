package ru.rutcampustrack.academic.exception;

/**
 * Thrown when request data is semantically invalid (beyond Jakarta Validation).
 * GlobalExceptionHandler maps this to 400 Bad Request.
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
