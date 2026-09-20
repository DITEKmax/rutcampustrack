package ru.rutcampustrack.attendance.student;

import java.time.Instant;

public final class StudentCheckinException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        INVALID_IDEMPOTENCY_KEY,
        INVALID_SESSION,
        WRONG_ROLE,
        OUT_OF_SCOPE,
        LESSON_NOT_FOUND,
        CHECKIN_COOLDOWN,
        MANUAL_ABSENCE_REQUIRES_APPEAL,
        IDEMPOTENCY_PAYLOAD_MISMATCH,
        CHECKIN_NOT_ELIGIBLE,
        DEPENDENCY_UNAVAILABLE
    }

    private final Code code;
    private final Instant retryAt;

    public StudentCheckinException(Code code, String message) {
        this(code, message, null);
    }

    public StudentCheckinException(Code code, String message, Instant retryAt) {
        super(message);
        this.code = code;
        this.retryAt = retryAt;
    }

    public Code code() {
        return code;
    }

    public Instant retryAt() {
        return retryAt;
    }
}
