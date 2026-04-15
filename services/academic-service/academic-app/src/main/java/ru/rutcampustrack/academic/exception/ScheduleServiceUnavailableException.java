package ru.rutcampustrack.academic.exception;

/**
 * Phase 61 / D-04: выбрасывается из {@code ScheduleGrpcClient}, когда gRPC-вызов
 * к schedule-service не увенчался успехом по любой причине кроме {@code NOT_FOUND}
 * (deadline, UNAVAILABLE, INTERNAL, …).
 *
 * <p>Маппится в 503 через {@link GlobalExceptionHandler} — клиент не получает
 * stack trace (mitigation T-61-06).
 */
public class ScheduleServiceUnavailableException extends RuntimeException {

    public ScheduleServiceUnavailableException(String message) {
        super(message);
    }

    public ScheduleServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
