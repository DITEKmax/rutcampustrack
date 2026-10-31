package ru.rutcampustrack.auth.service;

public final class AdminGeneratedPasswordException extends RuntimeException {
    public enum Code {
        INVALID_REQUEST,
        CREDENTIAL_VERSION_CONFLICT,
        CREDENTIAL_TARGET_UNAVAILABLE,
        CREDENTIAL_TARGET_NOT_FOUND
    }

    private final Code code;

    public AdminGeneratedPasswordException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
