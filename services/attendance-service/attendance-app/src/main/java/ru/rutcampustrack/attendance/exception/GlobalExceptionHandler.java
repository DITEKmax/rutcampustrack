package ru.rutcampustrack.attendance.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.Instant;

/**
 * Domain-level exception handler для attendance-service.
 *
 * <p>M11 G0.6: Spring MVC catch-all (validation/noHandler/general)
 * делегирован в shared-web GlobalExceptionHandler через
 * {@code @Order(LOWEST_PRECEDENCE)}. Attendance handler с
 * {@code @Order(HIGHEST_PRECEDENCE)} обрабатывает только domain.
 *
 * <p>Сохранены:
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 (attendance-domain)</li>
 *   <li>{@link AccessDeniedException} → 403 (attendance-custom, НЕ
 *       Spring Security)</li>
 *   <li>{@link BadRequestException} → 400</li>
 *   <li>{@link ConflictException} → 409</li>
 *   <li>{@link DuplicateKeyException} → 409 (MongoDB-specific)</li>
 *   <li>{@link GeofenceViolationException} → 422 (вне зоны)</li>
 *   <li>{@link GeofenceBlockedException} → 403 (геоотметка blocked)</li>
 *   <li>{@link RateLimitException} → 429</li>
 *   <li>{@link ScheduleServiceUnavailableException} +
 *       {@link AcademicServiceUnavailableException} → 503 (gRPC fallback)</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    /** MDC key для correlation id. */
    private static final String MDC_TRACE_ID = "traceId";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found",
                "Ресурс не найден", ex.getMessage(), request);
    }

    /**
     * Attendance-custom {@link AccessDeniedException} (НЕ Spring Security).
     * Spring Security AccessDeniedException обрабатывается shared handler'ом.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied",
                "Доступ запрещён", ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request",
                "Неверный запрос", ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "conflict",
                "Конфликт данных", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "duplicate-key",
                "Запись уже существует",
                ex.getMostSpecificCause().getMessage(), request);
    }

    @ExceptionHandler(GeofenceViolationException.class)
    public ResponseEntity<ErrorResponse> handleGeofenceViolation(
            GeofenceViolationException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "geofence-violation",
                "Вне зоны геофенса", ex.getMessage(), request);
    }

    @ExceptionHandler(ReportValidationException.class)
    public ResponseEntity<ErrorResponse> handleReportValidation(
            ReportValidationException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "report-validation-failed",
                "Report cannot be generated", ex.getMessage(), request);
    }

    @ExceptionHandler(GeofenceBlockedException.class)
    public ResponseEntity<ErrorResponse> handleGeofenceBlocked(
            GeofenceBlockedException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "geo-blocked",
                "Геоотметка заблокирована", ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitException ex,
                                                         HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
                "Превышен лимит запросов", ex.getMessage(), request);
    }

    @ExceptionHandler({
            ScheduleServiceUnavailableException.class,
            AcademicServiceUnavailableException.class,
            ReportExportUnavailableException.class
    })
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(RuntimeException ex,
                                                                  HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable",
                "Сервис недоступен", ex.getMessage(), request);
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String problemType,
            String title,
            String detail,
            HttpServletRequest request) {
        String traceId = MDC.get(MDC_TRACE_ID);
        ErrorResponse body = new ErrorResponse(
                status.value(),
                ErrorResponse.PROBLEM_BASE + problemType,
                title,
                detail,
                request.getRequestURI(),
                Instant.now(),
                traceId,
                null,
                null,
                null);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
