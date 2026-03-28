package ru.rutcampustrack.auth.exception;

public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException() {
        super("Invalid or expired refresh token");
    }

    public TokenRefreshException(String message) {
        super(message);
    }
}
