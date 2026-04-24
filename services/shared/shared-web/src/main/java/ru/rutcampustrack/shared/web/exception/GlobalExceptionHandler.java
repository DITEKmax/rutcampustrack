package ru.rutcampustrack.shared.web.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import ru.rutcampustrack.shared.web.api.exception.FieldError;

import java.time.Instant;
import java.util.List;

/**
 * Централизованный RFC 9457 handler для стандартных Spring MVC исключений +
 * catch-all.
 *
 * <p>M11 G0: единый handler для catch-all Spring MVC exceptions
 * ({@code @Order(Ordered.LOWEST_PRECEDENCE)}). Per-service
 * {@code @RestControllerAdvice} с {@code @Order(HIGHEST_PRECEDENCE)}
 * добавляют domain exceptions (ConflictException, GeofenceViolation,
 * InvalidCredentials, и т.п.).
 *
 * <p>Настройка требует:
 * {@code spring.mvc.throw-exception-if-no-handler-found=true} +
 * {@code spring.web.resources.add-mappings=false} для работы
 * {@link NoHandlerFoundException}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** MDC key для correlation id (совпадает с micrometer observation propagation). */
    public static final String MDC_TRACE_ID = "traceId";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getRejectedValue(), fe.getDefaultMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", "Ошибка валидации",
                "Одно или несколько полей не прошли проверку", request, fields, null, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(
                        v.getPropertyPath().toString(),
                        v.getInvalidValue(),
                        v.getMessage()))
                .toList();
        return problem(HttpStatus.BAD_REQUEST, "validation-failed", "Ошибка валидации",
                "Один или несколько параметров не прошли проверку", request, fields, null, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "malformed-request", "Невалидный JSON",
                "Тело запроса не может быть прочитано", request, null, null, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "media-type-not-supported",
                "Тип контента не поддерживается",
                "Content-Type не поддерживается для этого endpoint'а",
                request, null, null, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        FieldError fe = new FieldError(ex.getParameterName(), null, "Параметр обязателен");
        return problem(HttpStatus.BAD_REQUEST, "missing-parameter", "Отсутствует параметр",
                "Обязательный параметр запроса отсутствует", request, List.of(fe), null, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        FieldError fe = new FieldError(
                ex.getName(),
                ex.getValue(),
                "Неверный тип значения");
        return problem(HttpStatus.BAD_REQUEST, "type-mismatch", "Неверный тип параметра",
                "Значение параметра не соответствует ожидаемому типу", request, List.of(fe), null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed",
                "HTTP метод не поддерживается",
                "Метод " + ex.getMethod() + " не разрешён для этого endpoint'а",
                request, null, null, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            NoHandlerFoundException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Ресурс не найден",
                "Endpoint не существует", request, null, null, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found", "Ресурс не найден",
                ex.getMessage(), request, null, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", "Доступ запрещён",
                ex.getMessage(), request, null, null, null);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponseException(
            ErrorResponseException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return problem(status, "error", status.getReasonPhrase(),
                ex.getMessage(), request, null, null, null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        return problem(status, "error", status.getReasonPhrase(),
                ex.getReason(), request, null, null, null);
    }

    /**
     * Catch-all для необработанных исключений. Лог на ERROR с correlation id (P2-3/1),
     * в ответ — generic 500 без утечки стек-трейса.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        String traceId = MDC.get(MDC_TRACE_ID);
        log.error("Unhandled exception correlation={} uri={}", traceId, request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Внутренняя ошибка сервера",
                "Произошла внутренняя ошибка. Попробуйте позже.",
                request, null, null, null);
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String problemType,
            String title,
            String detail,
            HttpServletRequest request,
            List<FieldError> fieldErrors,
            String field,
            java.util.Map<String, Object> extras) {
        String traceId = MDC.get(MDC_TRACE_ID);
        ErrorResponse body = new ErrorResponse(
                status.value(),
                ErrorResponse.PROBLEM_BASE + problemType,
                title,
                detail,
                request.getRequestURI(),
                Instant.now(),
                traceId,
                fieldErrors,
                field,
                extras);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
