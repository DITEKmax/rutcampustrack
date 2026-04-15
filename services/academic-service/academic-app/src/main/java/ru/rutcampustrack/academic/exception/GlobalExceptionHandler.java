package ru.rutcampustrack.academic.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import ru.rutcampustrack.academic.contract.exception.ErrorResponse;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralized exception handler returning RFC 7807 Problem Details responses.
 * Controllers only throw exceptions — this handler maps them to HTTP responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String PROBLEM_BASE = "https://api.rutcampustrack.ru/problems/";

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
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                PROBLEM_BASE + "resource-not-found",
                "Ресурс не найден",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                PROBLEM_BASE + "access-denied",
                "Доступ запрещён",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                PROBLEM_BASE + "bad-request",
                "Неверный запрос",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null,
                ex.getField()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link ConflictException} — services' explicit pre-check path
     * (BUG-006-2). Surfaces the structured {@code field} in the RFC 7807 body
     * so the frontend can highlight the offending control.
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                         HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                PROBLEM_BASE + "conflict",
                "Конфликт данных",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null,
                ex.getField()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * Race-condition / defensive handler for DB-level unique-constraint
     * violations that slipped past the service pre-check (BUG-006-2,
     * T-58-02-02). Extracts the constraint name from the Hibernate/PG message
     * and maps it to a known field; unknown constraints become generic 500s
     * with a WARN log (never leak raw SQL text).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String constraintName = extractConstraintName(ex);
        String field = constraintName == null ? null : CONSTRAINT_TO_FIELD.get(constraintName);

        if (field != null) {
            String detail = FIELD_DETAIL.getOrDefault(field,
                    "Значение поля уже используется");
            ErrorResponse body = new ErrorResponse(
                    HttpStatus.CONFLICT.value(),
                    PROBLEM_BASE + "conflict",
                    "Конфликт данных",
                    detail,
                    request.getRequestURI(),
                    Instant.now(),
                    null,
                    field
            );
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        // Unknown constraint → fall back to 500. Log the constraint name so ops
        // can add it to CONSTRAINT_TO_FIELD; never include raw SQL in response.
        log.warn("Unmapped data-integrity violation constraint={} message={}",
                constraintName, ex.getMostSpecificCause().getMessage());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                PROBLEM_BASE + "internal-error",
                "Внутренняя ошибка сервера",
                "Нарушение целостности данных",
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static String extractConstraintName(DataIntegrityViolationException ex) {
        // Walk the cause chain looking for a message that contains the marker.
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                PROBLEM_BASE + "validation-failed",
                "Ошибка валидации",
                "Одно или несколько полей не прошли проверку",
                request.getRequestURI(),
                Instant.now(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                           HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                PROBLEM_BASE + "resource-not-found",
                "Ресурс не найден",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex,
                                                          HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                PROBLEM_BASE + "resource-not-found",
                "Ресурс не найден",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponse(ErrorResponseException ex,
                                                              HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse body = new ErrorResponse(
                status.value(),
                PROBLEM_BASE + "error",
                status.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                               HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorResponse body = new ErrorResponse(
                status.value(),
                PROBLEM_BASE + "error",
                status.getReasonPhrase(),
                ex.getReason(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Phase 61 / T-61-06: gRPC-вызов к schedule-service упал (UNAVAILABLE, deadline, …).
     * Отдаём 503 без стек-трейса — клиент видит только короткий детейл на русском.
     */
    @ExceptionHandler(ScheduleServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleScheduleUnavailable(
            ScheduleServiceUnavailableException ex,
            HttpServletRequest request) {
        log.warn("schedule-service недоступен: {}", ex.getMessage());
        ErrorResponse body = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                PROBLEM_BASE + "schedule-service-unavailable",
                "Сервис расписания недоступен",
                "Не удалось связаться с сервисом расписания. Попробуйте позже.",
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex,
                                                        HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                PROBLEM_BASE + "internal-error",
                "Внутренняя ошибка сервера",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
