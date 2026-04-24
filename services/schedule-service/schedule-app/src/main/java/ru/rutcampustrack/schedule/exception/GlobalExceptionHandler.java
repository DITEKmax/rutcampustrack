package ru.rutcampustrack.schedule.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.Instant;

/**
 * Domain-level exception handler для schedule-service.
 *
 * <p>M11 G0.5: Spring MVC catch-all (validation/noHandler/general)
 * делегирован в shared-web GlobalExceptionHandler через
 * {@code @Order(LOWEST_PRECEDENCE)}. Schedule handler имеет
 * {@code @Order(HIGHEST_PRECEDENCE)} и обрабатывает только domain.
 *
 * <p>Сохранены:
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 (schedule-domain)</li>
 *   <li>{@link AcademicServiceUnavailableException} → 503 (gRPC fallback)</li>
 *   <li>{@link InvalidLessonStateException} → 422 (lesson FSM)</li>
 *   <li>{@link DataIntegrityViolationException} → 409 с
 *       schedule-specific {@code uq_one_off_slot} hint</li>
 *   <li>{@link ConflictException} → 409 (domain pre-check)</li>
 *   <li>{@link AccessDeniedException} → 403 (schedule custom, НЕ
 *       Spring Security AccessDeniedException — у того свой handler
 *       в shared)</li>
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
                "Resource not found", ex.getMessage(), request);
    }

    @ExceptionHandler(AcademicServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAcademicUnavailable(
            AcademicServiceUnavailableException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable",
                "Academic Service unavailable", ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidLessonStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLessonState(
            InvalidLessonStateException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-lesson-state",
                "Invalid lesson state transition", ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String message = ex.getMessage() != null && ex.getMessage().contains("uq_one_off_slot")
                ? "Разовая пара на этот слот уже существует"
                : ex.getMessage();
        return problem(HttpStatus.CONFLICT, "conflict",
                "Data conflict", message, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "conflict",
                "Conflict", ex.getMessage(), request);
    }

    /**
     * Schedule-custom {@link AccessDeniedException} (НЕ Spring Security).
     * Spring Security AccessDeniedException обрабатывается shared handler'ом.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied",
                "Access denied", ex.getMessage(), request);
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
