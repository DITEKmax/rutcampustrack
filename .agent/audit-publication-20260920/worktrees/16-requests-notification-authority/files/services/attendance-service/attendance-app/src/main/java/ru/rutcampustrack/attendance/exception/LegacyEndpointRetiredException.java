package ru.rutcampustrack.attendance.exception;

/** Signals that a legacy student request create endpoint is permanently retired. */
public final class LegacyEndpointRetiredException extends RuntimeException {
    public LegacyEndpointRetiredException() {
        super("Используйте канонический student requests API");
    }
}
