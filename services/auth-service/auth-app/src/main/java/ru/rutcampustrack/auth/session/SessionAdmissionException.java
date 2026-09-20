package ru.rutcampustrack.auth.session;

import java.util.Objects;

/**
 * Typed, bearer-free failure from the internal session-admission boundary.
 */
public final class SessionAdmissionException extends RuntimeException {

    public enum Code {
        INVALID_SESSION,
        SESSION_REVOKED,
        ROLE_NOT_GRANTED,
        ROLE_NOT_SELECTABLE,
        SESSION_STATE_STALE,
        AUTHORITY_UNAVAILABLE
    }

    private final Code code;

    public SessionAdmissionException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public SessionAdmissionException(Code code, Throwable cause) {
        super(Objects.requireNonNull(code, "code").name(), cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    @Override
    public String toString() {
        return "SessionAdmissionException[code=" + code + "]";
    }
}
