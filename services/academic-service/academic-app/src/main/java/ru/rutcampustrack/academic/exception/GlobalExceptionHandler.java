package ru.rutcampustrack.academic.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain-level exception handler для academic-service.
 *
 * <p>M11 G0.4: catch-all Spring MVC exceptions (validation/noHandler/
 * accessDenied/general) делегированы в
 * {@code ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler}
 * через {@code @Order(LOWEST_PRECEDENCE)}. Этот advice сидит выше
 * приоритетом ({@code @Order(HIGHEST_PRECEDENCE)}) и обрабатывает
 * только academic-domain исключения.
 *
 * <p>Сохранены:
 * <ul>
 *   <li>{@link ResourceNotFoundException} → 404 (academic-specific)</li>
 *   <li>{@link AccessDeniedException} → 403 (academic-specific, НЕ Spring
 *       Security AccessDeniedException — у того свой handler в shared)</li>
 *   <li>{@link BadRequestException} → 400 + structured field</li>
 *   <li>{@link ConflictException} → 409 + field + extras (BUG-006-2)</li>
 *   <li>{@link DataIntegrityViolationException} → 409 с constraint
 *       mapping (academic constraint dictionary)</li>
 *   <li>{@link ScheduleServiceUnavailableException} → 503 (Phase 61
 *       gRPC fallback)</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** MDC key для correlation id (совпадает с shared GlobalExceptionHandler). */
    private static final String MDC_TRACE_ID = "traceId";

    /**
     * Maps PostgreSQL {@code UNIQUE} constraint names to logical DTO field names
     * (BUG-006-2, D-05). Names follow the default Postgres convention
     * {@code <table>_<column>_key} for implicit unique constraints.
     */
    private static final Map<String, String> CONSTRAINT_TO_FIELD = Map.of(
            "users_login_key", "login",
            "users_email_key", "email",
            "users_telegram_id_key", "telegramId",
            "users_employee_number_key", "employeeNumber",
            "groups_name_key", "name",
            "semesters_no_overlap", "dates"
    );

    /** Matches {@code constraint "xxx"} fragment in PG/Hibernate error messages. */
    private static final Pattern CONSTRAINT_PATTERN =
            Pattern.compile("constraint\\s+\"([^\"]+)\"");

    /** Russian, user-facing details per field (DSL: leak only the field name). */
    private static final Map<String, String> FIELD_DETAIL = Map.of(
            "login", "Логин уже используется. Выберите другой",
            "email", "Email уже зарегистрирован",
            "telegramId", "Telegram ID уже привязан к другой учётной записи",
            "employeeNumber", "Табельный номер уже используется",
            "name", "Название уже используется",
            "dates", "Даты семестра пересекаются с существующим"
    );

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "resource-not-found",
                "Ресурс не найден", ex.getMessage(), request, null, null);
    }

    /**
     * Academic-specific {@link AccessDeniedException} (НЕ Spring Security).
     * Spring Security AccessDeniedException обрабатывается shared handler'ом.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied",
                "Доступ запрещён", ex.getMessage(), request, null, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                          HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request",
                "Неверный запрос", ex.getMessage(), request, ex.getField(), null);
    }

    /**
     * {@link ConflictException} — services' explicit pre-check path
     * (BUG-006-2). Surfaces the structured {@code field} + {@code extras}
     * in the RFC 9457 body so the frontend can highlight the offending
     * control / show counts.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                        HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "conflict",
                "Конфликт данных", ex.getMessage(), request, ex.getField(), ex.getExtras());
    }

    /**
     * Race-condition / defensive handler for DB-level unique-constraint
     * violations that slipped past the service pre-check (BUG-006-2,
     * T-58-02-02). Extracts the constraint name from the Hibernate/PG
     * message and maps it to a known field; unknown constraints fall back
     * to shared handler's catch-all 500 (no field, generic detail).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String constraintName = extractConstraintName(ex);
        String field = constraintName == null ? null : CONSTRAINT_TO_FIELD.get(constraintName);

        if (field != null) {
            String detail = FIELD_DETAIL.getOrDefault(field, "Значение поля уже используется");
            return problem(HttpStatus.CONFLICT, "conflict",
                    "Конфликт данных", detail, request, field, null);
        }

        // Unknown constraint → 500 без утечки SQL. Лог для ops добавить
        // constraint в CONSTRAINT_TO_FIELD при необходимости.
        log.warn("Unmapped data-integrity violation constraint={} message={}",
                constraintName, ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Внутренняя ошибка сервера", "Нарушение целостности данных",
                request, null, null);
    }

    private static String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable t = ex;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                Matcher m = CONSTRAINT_PATTERN.matcher(msg);
                if (m.find()) {
                    return m.group(1);
                }
            }
            t = t.getCause();
        }
        return null;
    }

    /**
     * Phase 61 / T-61-06: gRPC-вызов к schedule-service упал
     * (UNAVAILABLE, deadline, …). Отдаём 503 без стек-трейса.
     */
    @ExceptionHandler(ScheduleServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleScheduleUnavailable(
            ScheduleServiceUnavailableException ex,
            HttpServletRequest request) {
        log.warn("schedule-service недоступен: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "schedule-service-unavailable",
                "Сервис расписания недоступен",
                "Не удалось связаться с сервисом расписания. Попробуйте позже.",
                request, null, null);
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String problemType,
            String title,
            String detail,
            HttpServletRequest request,
            String field,
            Map<String, Object> extras) {
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
                field,
                extras);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
