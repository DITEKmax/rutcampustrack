package ru.rutcampustrack.auth.device;

public final class DevicePreferencesException extends RuntimeException {
    public enum Code {
        DEVICE_CONTEXT_LOST,
        DEVICE_REVISION_CONFLICT,
        DEVICE_PREFERENCES_UNAVAILABLE,
        INVALID_REQUEST
    }

    private final Code code;

    public DevicePreferencesException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
