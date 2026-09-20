package ru.rutcampustrack.academic.map;

/** Typed, non-sensitive failures exposed by the campus-map read RPCs. */
public final class CampusMapReadException extends RuntimeException {
    public enum Code {
        INVALID_ARGUMENT,
        PERMISSION_DENIED,
        NOT_FOUND,
        UNAVAILABLE,
        FAILED_PRECONDITION,
        DATA_LOSS,
        RESOURCE_EXHAUSTED,
        INTERNAL
    }

    private final Code code;

    private CampusMapReadException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static CampusMapReadException of(Code code, String message) {
        return new CampusMapReadException(code, message, null);
    }

    public static CampusMapReadException of(Code code, String message, Throwable cause) {
        return new CampusMapReadException(code, message, cause);
    }

    public Code code() {
        return code;
    }
}
