package ru.rutcampustrack.attendance.grpc;

/** Adapter-level failure with a stable public transport code. */
final class StudentRequestTransportException extends RuntimeException {
    private final StudentRequestErrorCode code;

    StudentRequestTransportException(StudentRequestErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    StudentRequestErrorCode code() {
        return code;
    }
}
