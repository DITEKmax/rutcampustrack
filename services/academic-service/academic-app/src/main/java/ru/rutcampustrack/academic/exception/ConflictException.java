package ru.rutcampustrack.academic.exception;

/**
 * Thrown when a request conflicts with the current state of a resource
 * (e.g. duplicate login, group already has a headman).
 *
 * <p>Optionally carries the offending {@link #getField() field} name, so that
 * {@link GlobalExceptionHandler} can surface it in the RFC 7807
 * {@code ProblemDetail} body and the frontend can render a field-specific
 * message (BUG-006-2, D-05/D-07).
 *
 * <p>Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    private final String field;
    private final Object value;

    /**
     * Legacy constructor — no field information. Prefer the 3-arg overload.
     */
    public ConflictException(String message) {
        super(message);
        this.field = null;
        this.value = null;
    }

    /**
     * Structured conflict carrying the DTO field name and rejected value.
     *
     * @param field   logical camelCase field name used by the frontend
     *                (e.g. {@code "login"}, {@code "telegramId"}).
     * @param value   the value that triggered the conflict. Not serialized
     *                into the response body (to avoid leaking PII).
     * @param message human-readable detail, already localised.
     */
    public ConflictException(String field, Object value, String message) {
        super(message);
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }
}
