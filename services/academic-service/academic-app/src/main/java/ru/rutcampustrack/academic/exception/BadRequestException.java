package ru.rutcampustrack.academic.exception;

/**
 * Thrown when the request contains semantically invalid data that passes
 * bean validation but fails business rules (e.g. date range reversed).
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
