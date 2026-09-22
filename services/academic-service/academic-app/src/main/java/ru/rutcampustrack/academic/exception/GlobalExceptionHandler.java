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
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.contract.dto.homework.HomeworkPublicationPendingResponse;
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
    private static final Map<String, String> CONSTRAINT_TO_FIELD = Map.ofEntries(
            Map.entry("users_login_key", "login"),
            Map.entry("users_email_key", "email"),
            Map.entry("users_telegram_id_key", "telegramId"),
            Map.entry("users_employee_number_key", "employeeNumber"),
            Map.entry("groups_name_key", "name"),
            Map.entry("groups_code_pair_uq", "numericCode"),
            Map.entry("user_role_grants_active_headman_group_uq", "groupId"),
            Map.entry("semesters_no_overlap", "dates"),
            Map.entry("assignments_no_same_teacher_overlap", "validFrom"),
            Map.entry("assignments_validity_chk", "validUntilExclusive"),
            Map.entry("homeworks_binding_uq", "bindingId"),
            Map.entry("homeworks_actor_request_uq", "requestKey")
    );

    /** Matches {@code constraint "xxx"} fragment in PG/Hibernate error messages. */
    private static final Pattern CONSTRAINT_PATTERN =
            Pattern.compile("constraint\\s+\"([^\"]+)\"");

    /** Russian, user-facing details per field (DSL: leak only the field name). */
    private static final Map<String, String> FIELD_DETAIL = Map.ofEntries(
            Map.entry("login", "Логин уже используется. Выберите другой"),
            Map.entry("email", "Email уже зарегистрирован"),
            Map.entry("telegramId", "Telegram ID уже привязан к другой учётной записи"),
            Map.entry("employeeNumber", "Табельный номер уже используется"),
            Map.entry("name", "Название уже используется"),
            Map.entry("numericCode", "Код группы уже используется"),
            Map.entry("groupId", "В группе уже назначен другой староста"),
            Map.entry("dates", "Даты семестров пересекаются с существующим"),
            Map.entry("validFrom", "Период назначения пересекается с существующим"),
            Map.entry("validUntilExclusive", "Недопустимые границы периода назначения"),
            Map.entry("bindingId", "Привязка домашнего задания уже используется"),
            Map.entry("requestKey", "Ключ идемпотентности уже использован")
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

    @ExceptionHandler(HomeworkPublicationPendingException.class)
    public ResponseEntity<HomeworkPublicationPendingResponse> handleHomeworkPublicationPending(
            HomeworkPublicationPendingException ex) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ex.getResponse());
    }

    @ExceptionHandler(HistoricalMembershipException.class)
    public ResponseEntity<ErrorResponse> handleHistoricalMembership(
            HistoricalMembershipException ex,
            HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FAILED_PRECONDITION, UNSUPPORTED_MUTATION -> HttpStatus.CONFLICT;
        };
        return problem(status, "historical-membership-" + ex.code().name().toLowerCase(),
                "Операция с историей посещаемости недоступна", ex.getMessage(),
                request, null, null);
    }

    @ExceptionHandler(AssignmentClosureNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentClosureNotReady(
            AssignmentClosureNotReadyException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "assignment-closure-not-ready",
                "Закрытие назначения пока недоступно", ex.getMessage(), request, null, null);
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
        // The legacy POST contract reports a normalized code collision as a
        // name conflict; the additive registry endpoint owns the split-code
        // field.  Keep the distinction even when the pre-check loses a race
        // with the database unique index.
        if ("groups_name_key".equals(constraintName)
                || "groups_code_pair_uq".equals(constraintName)) {
            field = isRegistryRequest(request) ? "numericCode" : "name";
        }

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

    private static boolean isRegistryRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/academic/groups/registry");
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
