package ru.rutcampustrack.attendance.exception;

/** Stable type for exact upload-limit mapping to HTTP/gRPC 413. */
public final class PayloadTooLargeException extends BadRequestException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
