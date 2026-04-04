package ru.rutcampustrack.attendance.exception;

/**
 * Thrown when a student exceeds the rate limit for checkin attempts.
 * GlobalExceptionHandler maps this to HTTP 429 Too Many Requests.
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
