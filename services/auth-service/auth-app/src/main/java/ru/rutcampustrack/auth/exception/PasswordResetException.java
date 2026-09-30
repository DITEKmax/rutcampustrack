package ru.rutcampustrack.auth.exception;

import java.util.Objects;

/** Typed, secret-free failures for the unauthenticated password-reset flow. */
public final class PasswordResetException extends RuntimeException {

    public enum Code {
        OTP_INVALID,
        OTP_EXPIRED,
        OTP_RATE_LIMITED,
        RESET_TICKET_INVALID
    }

    private final Code code;
    private final Integer attemptsRemaining;
    private final Integer retryAfterSeconds;

    public PasswordResetException(Code code, Integer attemptsRemaining, Integer retryAfterSeconds) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
        this.attemptsRemaining = attemptsRemaining;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Code code() {
        return code;
    }

    public Integer attemptsRemaining() {
        return attemptsRemaining;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public String toString() {
        return "PasswordResetException[code=" + code
                + ", attemptsRemaining=" + attemptsRemaining
                + ", retryAfterSeconds=" + retryAfterSeconds + ']';
    }
}
