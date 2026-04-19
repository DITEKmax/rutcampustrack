package ru.rutcampustrack.gateway.security;

/**
 * Raised when auth-service token-exchange endpoint is unreachable or returns an error.
 * Filter maps this to 503 Problem Details so the client knows to retry (not retry-forever).
 */
public class InternalIssuerUnavailableException extends RuntimeException {

    public InternalIssuerUnavailableException(String message) {
        super(message);
    }

    public InternalIssuerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
