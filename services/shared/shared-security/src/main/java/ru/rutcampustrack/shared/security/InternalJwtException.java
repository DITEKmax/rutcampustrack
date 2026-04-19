package ru.rutcampustrack.shared.security;

/**
 * Raised when Internal JWT validation fails. Downstream filter converts
 * this to 401 Problem Details response.
 */
public class InternalJwtException extends RuntimeException {

    public InternalJwtException(String message) {
        super(message);
    }

    public InternalJwtException(String message, Throwable cause) {
        super(message, cause);
    }
}
