package ru.rutcampustrack.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.controller.DevicePreferencesController;
import ru.rutcampustrack.auth.device.DevicePreferencesException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

@RestControllerAdvice(assignableTypes = DevicePreferencesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class DevicePreferencesExceptionHandler {
    @ExceptionHandler(DevicePreferencesException.class)
    public ResponseEntity<ErrorResponse> denied(
            DevicePreferencesException error, HttpServletRequest request) {
        HttpStatus status =
                switch (error.code()) {
                    case DEVICE_CONTEXT_LOST, DEVICE_REVISION_CONFLICT -> HttpStatus.CONFLICT;
                    case DEVICE_PREFERENCES_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                    case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
                };
        return response(status, error.code().name(), request);
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> invalid(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request);
    }

    private ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(
                        new ErrorResponse(
                                status.value(),
                                ErrorResponse.PROBLEM_BASE + "device-preferences-failed",
                                "Запрос настроек устройства отклонён",
                                "Запрос не может быть выполнен",
                                request.getRequestURI(),
                                Instant.now(),
                                MDC.get("traceId"),
                                null,
                                null,
                                Map.of("code", code)));
    }
}
