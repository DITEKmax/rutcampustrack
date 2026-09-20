package ru.rutcampustrack.academic.studentprojection;

/**
 * Typed failure for the signed student projection boundary.
 *
 * <p>The gRPC adapter maps {@link Code} to a transport status and a stable
 * protobuf trailer.  Callers must never infer the reason from exception text.
 */
public final class StudentProjectionException extends RuntimeException {

    public enum Code {
        INVALID_REQUEST,
        INVALID_SESSION,
        WRONG_ROLE,
        OUT_OF_SCOPE,
        STUDENT_SCOPE_UNRESOLVED,
        DEPENDENCY_UNAVAILABLE,
        INCONSISTENT_SOURCE
    }

    private final Code code;

    public StudentProjectionException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public StudentProjectionException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }

    public static StudentProjectionException invalidRequest(String message) {
        return new StudentProjectionException(Code.INVALID_REQUEST, message);
    }

    public static StudentProjectionException invalidSession(String message) {
        return new StudentProjectionException(Code.INVALID_SESSION, message);
    }

    public static StudentProjectionException wrongRole(String message) {
        return new StudentProjectionException(Code.WRONG_ROLE, message);
    }

    public static StudentProjectionException outOfScope(String message) {
        return new StudentProjectionException(Code.OUT_OF_SCOPE, message);
    }

    public static StudentProjectionException unresolved(String message) {
        return new StudentProjectionException(Code.STUDENT_SCOPE_UNRESOLVED, message);
    }

    public static StudentProjectionException dependency(String message, Throwable cause) {
        return new StudentProjectionException(Code.DEPENDENCY_UNAVAILABLE, message, cause);
    }

    public static StudentProjectionException inconsistent(String message) {
        return new StudentProjectionException(Code.INCONSISTENT_SOURCE, message);
    }
}
