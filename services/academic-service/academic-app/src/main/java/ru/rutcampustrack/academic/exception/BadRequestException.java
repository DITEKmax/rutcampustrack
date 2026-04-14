package ru.rutcampustrack.academic.exception;

/**
 * Thrown when the request contains semantically invalid data that passes
 * bean validation but fails business rules (e.g. date range reversed, or
 * a role-conditional required field is missing — BUG-006-3).
 * Mapped to HTTP 400 by {@link GlobalExceptionHandler}.
 *
 * <p>Optionally carries the offending {@link #getField() field} name so that
 * {@code GlobalExceptionHandler} can surface it in the RFC 7807
 * {@code ProblemDetail} body and the frontend can highlight the control.
 */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String message) {
        super(message);
        this.field = null;
    }

    /**
     * Structured bad-request carrying the DTO field name.
     *
     * @param field   logical camelCase field name used by the frontend
     *                (e.g. {@code "telegramId"}).
     * @param message human-readable detail, already localised.
     */
    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
