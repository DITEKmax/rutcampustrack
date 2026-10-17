package ru.rutcampustrack.attendance.exception;

/** Availability mapping is distinct from an explicitly retryable transport status. */
public class ScheduleServiceUnavailableException extends RuntimeException {
    private final io.grpc.Status.Code statusCode;

    public ScheduleServiceUnavailableException(String message) {
        this(message, null);
    }

    public ScheduleServiceUnavailableException(String message, io.grpc.Status.Code statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public boolean isRetryable() {
        return statusCode == io.grpc.Status.Code.UNAVAILABLE
                || statusCode == io.grpc.Status.Code.DEADLINE_EXCEEDED;
    }
}
