package ru.rutcampustrack.attendance.exception;

/** Availability mapping is distinct from an explicitly retryable transport status. */
public class ScheduleServiceUnavailableException extends RuntimeException {
    public enum UnavailableOrigin {
        CONNECT,
        DNS,
        TLS,
        CHANNEL_SHUTDOWN,
        TRANSPORT_IO,
        SERVER_UNAVAILABLE,
        UNKNOWN
    }

    private final io.grpc.Status.Code statusCode;
    private final UnavailableOrigin unavailableOrigin;

    public ScheduleServiceUnavailableException(String message) {
        this(message, null);
    }

    public ScheduleServiceUnavailableException(String message, io.grpc.Status.Code statusCode) {
        super(message);
        this.statusCode = statusCode;
        this.unavailableOrigin = null;
    }

    public ScheduleServiceUnavailableException(
            String message, io.grpc.Status.Code statusCode, UnavailableOrigin unavailableOrigin) {
        super(message);
        this.statusCode = statusCode;
        this.unavailableOrigin =
                statusCode == io.grpc.Status.Code.UNAVAILABLE ? unavailableOrigin : null;
    }

    public UnavailableOrigin getUnavailableOrigin() {
        return unavailableOrigin;
    }

    public io.grpc.Status.Code getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return statusCode == io.grpc.Status.Code.UNAVAILABLE
                || statusCode == io.grpc.Status.Code.DEADLINE_EXCEEDED;
    }
}
