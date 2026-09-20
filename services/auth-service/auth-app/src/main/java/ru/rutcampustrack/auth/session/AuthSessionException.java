package ru.rutcampustrack.auth.session;

import java.util.Objects;

/** Bearer-free typed failure for the public session contract. */
public final class AuthSessionException extends RuntimeException {

    public enum Code {
        PASSWORD_POLICY_VIOLATION,
        CURRENT_PASSWORD_INVALID,
        INVALID_CURSOR,
        INVALID_SESSION,
        SESSION_REVOKED,
        REFRESH_REJECTED,
        ROLE_NOT_GRANTED,
        ROLE_NOT_SELECTABLE,
        ROLE_READ_ONLY,
        BOOTSTRAP_SCOPE_DENIED,
        SESSION_STATE_STALE,
        SESSION_VERSION_CONFLICT,
        REFRESH_ALREADY_ROTATED,
        AUTHORITY_UNAVAILABLE
    }

    private final Code code;

    public AuthSessionException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public AuthSessionException(Code code, Throwable cause) {
        super(Objects.requireNonNull(code, "code").name(), cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    @Override
    public String toString() {
        return "AuthSessionException[code=" + code + ']';
    }
}
