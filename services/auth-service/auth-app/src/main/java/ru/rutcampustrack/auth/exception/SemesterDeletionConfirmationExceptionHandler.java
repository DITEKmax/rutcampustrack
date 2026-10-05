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
import org.springframework.http.converter.HttpMessageNotReadableException;
import ru.rutcampustrack.auth.controller.InternalSemesterDeletionConfirmationController;
import ru.rutcampustrack.auth.controller.InternalMapDeletionConfirmationController;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import ru.rutcampustrack.shared.web.api.exception.FieldError;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Keeps request secrets out of validation and denial responses for this internal route. */
@RestControllerAdvice(assignableTypes = {InternalSemesterDeletionConfirmationController.class,
        InternalMapDeletionConfirmationController.class,
        ru.rutcampustrack.auth.controller.InternalUserArchiveConfirmationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SemesterDeletionConfirmationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), null, error.getDefaultMessage()))
                .toList();
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), ErrorResponse.PROBLEM_BASE + "validation-failed",
                "Ошибка валидации", "Одно или несколько полей не прошли проверку",
                request.getRequestURI(), Instant.now(), MDC.get("traceId"), fields, null, null);
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request-body", request);
    }

    @ExceptionHandler(AuthSessionException.class)
    public ResponseEntity<ErrorResponse> handleSessionDenial(
            AuthSessionException exception,
            HttpServletRequest request) {
        HttpStatus status = switch (exception.code()) {
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_SESSION, SESSION_REVOKED, REFRESH_REJECTED,
                    SESSION_STATE_STALE, SESSION_VERSION_CONFLICT, REFRESH_ALREADY_ROTATED ->
                    HttpStatus.UNAUTHORIZED;
            case CURRENT_PASSWORD_INVALID, ROLE_NOT_GRANTED, ROLE_NOT_SELECTABLE,
                    ROLE_READ_ONLY, BOOTSTRAP_SCOPE_DENIED, PASSWORD_POLICY_VIOLATION,
                    INVALID_CURSOR -> HttpStatus.FORBIDDEN;
            case AUTHORITY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return problem(status, "confirmation-denied", request, Map.of("code", exception.code().name()));
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "confirmation-rate-limited", request,
                Map.of("code", "CONFIRMATION_RATE_LIMITED"));
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String type,
            HttpServletRequest request) {
        return problem(status, type, request, null);
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String type,
            HttpServletRequest request,
            Map<String, Object> extras) {
        ErrorResponse body = new ErrorResponse(
                status.value(), ErrorResponse.PROBLEM_BASE + type,
                "Подтверждение отклонено", "Запрос не может быть подтверждён",
                request.getRequestURI(), Instant.now(), MDC.get("traceId"), null, null, extras);
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
