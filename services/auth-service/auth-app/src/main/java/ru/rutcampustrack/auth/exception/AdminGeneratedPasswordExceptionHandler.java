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
import ru.rutcampustrack.auth.controller.AdminPasswordResetController;
import ru.rutcampustrack.auth.service.AdminGeneratedPasswordException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

@RestControllerAdvice(assignableTypes = AdminPasswordResetController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AdminGeneratedPasswordExceptionHandler {
    @ExceptionHandler(AdminGeneratedPasswordException.class)
    public ResponseEntity<ErrorResponse> denied(
            AdminGeneratedPasswordException exception, HttpServletRequest request) {
        HttpStatus status =
                switch (exception.code()) {
                    case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
                    case CREDENTIAL_VERSION_CONFLICT, CREDENTIAL_TARGET_UNAVAILABLE ->
                            HttpStatus.CONFLICT;
                    case CREDENTIAL_TARGET_NOT_FOUND -> HttpStatus.NOT_FOUND;
                };
        return response(status, exception.code().name(), request);
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
                                ErrorResponse.PROBLEM_BASE + "admin-credential-failed",
                                "Запрос доступа пользователя отклонён",
                                "Запрос не может быть выполнен",
                                request.getRequestURI(),
                                Instant.now(),
                                MDC.get("traceId"),
                                null,
                                null,
                                Map.of("code", code)));
    }
}
