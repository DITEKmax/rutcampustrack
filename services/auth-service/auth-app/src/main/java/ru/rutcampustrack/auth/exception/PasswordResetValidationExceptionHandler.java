package ru.rutcampustrack.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.auth.controller.PasswordResetController;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import ru.rutcampustrack.shared.web.api.exception.FieldError;

import java.time.Instant;
import java.util.List;

/** Redacts rejected request values only on public password-reset routes. */
@RestControllerAdvice(assignableTypes = PasswordResetController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class PasswordResetValidationExceptionHandler {

    private static final String MDC_TRACE_ID = "traceId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidResetRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), null, error.getDefaultMessage()))
                .toList();
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ErrorResponse.PROBLEM_BASE + "validation-failed",
                "Ошибка валидации",
                "Одно или несколько полей не прошли проверку",
                request.getRequestURI(),
                Instant.now(),
                MDC.get(MDC_TRACE_ID),
                fields,
                null,
                null);
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
