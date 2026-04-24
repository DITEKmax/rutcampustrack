package ru.rutcampustrack.shared.web.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Унифицированный формат ошибки API по RFC 9457 (обновлённый RFC 7807)
 * Problem Details. Единственный {@code ErrorResponse} во всём backend
 * (M11 G0 устранил 5 дублей: shared-web/InvalidParam,
 * academic-api-contract/ErrorResponse, schedule-api-contract,
 * attendance-api-contract, auth/dto).
 *
 * <p>Используется shared {@code GlobalExceptionHandler} (catch-all
 * Spring MVC exceptions, LOWEST_PRECEDENCE) и сервисными
 * {@code @RestControllerAdvice} с {@code @Order(HIGHEST_PRECEDENCE)}
 * для domain exceptions.
 *
 * <p>Расширения RFC 9457 (поля сверх стандарта):
 * <ul>
 *   <li>{@code traceId} — correlation ID из MDC (P2-3/1, шестнадцатеричный).</li>
 *   <li>{@code fieldErrors} — массив ошибок валидации полей DTO body
 *       (для 400 Bad Request).</li>
 *   <li>{@code field} — имя DTO-поля, вызвавшего конфликт (BUG-006-2,
 *       для 409 Conflict).</li>
 *   <li>{@code extras} — дополнительные данные для клиента (каскадное
 *       удаление, retry-after, и т.п.).</li>
 * </ul>
 *
 * @param status         HTTP статус код.
 * @param type           URI типа ошибки.
 * @param title          краткое описание.
 * @param detail         детальное описание.
 * @param instance       URI запроса.
 * @param timestamp      время возникновения (UTC).
 * @param traceId        correlation id из MDC (P2-3/1).
 * @param fieldErrors    ошибки валидации полей body (для 400).
 * @param field          имя DTO-поля, вызвавшего конфликт (BUG-006-2).
 * @param extras         дополнительные данные для клиента.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Стандартный формат ошибки API (RFC 9457 Problem Details)")
public record ErrorResponse(

        @Schema(description = "HTTP статус код", example = "404")
        int status,

        @Schema(description = "URI типа ошибки",
                example = "https://api.rutcampustrack.ru/problems/resource-not-found")
        String type,

        @Schema(description = "Краткое описание ошибки", example = "Ресурс не найден")
        String title,

        @Schema(description = "Детальное описание", example = "Группа с id=99 не найдена")
        String detail,

        @Schema(description = "URI запроса, вызвавшего ошибку",
                example = "/api/academic/groups/99")
        String instance,

        @Schema(description = "Время возникновения ошибки (UTC)",
                example = "2026-04-24T10:15:30Z")
        Instant timestamp,

        @Schema(description = "Correlation ID из MDC (для трассировки в логах)",
                example = "abc-trace-123")
        String traceId,

        @Schema(description = "Ошибки валидации полей body DTO (только для 400)")
        List<FieldError> fieldErrors,

        @Schema(description = "Имя поля DTO, вызвавшего конфликт (только для 409, BUG-006-2)",
                example = "login")
        String field,

        @Schema(description = "Дополнительные данные для клиента " +
                "(счётчики каскадного удаления, retry-after, и т.п.)",
                example = "{\"scheduleItemsCount\":3}")
        Map<String, Object> extras
) {

    /** Базовый URI типов ошибок (используется в {@code type}). */
    public static final String PROBLEM_BASE = "https://api.rutcampustrack.ru/problems/";

    // === Backward-compat конструкторы ===

    /**
     * 6-arg (auth-service legacy): без traceId, fieldErrors, field, extras.
     * Делегирует в canonical с null'ами.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp) {
        this(status, type, title, detail, instance, timestamp, null, null, null, null);
    }

    /**
     * 7-arg (schedule/attendance legacy): + fieldErrors, без traceId/field/extras.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp,
                         List<FieldError> fieldErrors) {
        this(status, type, title, detail, instance, timestamp, null, fieldErrors, null, null);
    }

    /**
     * 8-arg (academic legacy): + field. Без traceId, extras.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp,
                         List<FieldError> fieldErrors,
                         String field) {
        this(status, type, title, detail, instance, timestamp, null, fieldErrors, field, null);
    }

    /**
     * 9-arg (academic full): + extras.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp,
                         List<FieldError> fieldErrors,
                         String field,
                         Map<String, Object> extras) {
        this(status, type, title, detail, instance, timestamp, null, fieldErrors, field, extras);
    }

    // === Factory methods ===

    public static ErrorResponse badRequest(String detail, String instance, String traceId) {
        return new ErrorResponse(
                400,
                PROBLEM_BASE + "bad-request",
                "Неверный запрос",
                detail,
                instance,
                Instant.now(),
                traceId,
                null,
                null,
                null
        );
    }

    public static ErrorResponse notFound(String detail, String instance, String traceId) {
        return new ErrorResponse(
                404,
                PROBLEM_BASE + "resource-not-found",
                "Ресурс не найден",
                detail,
                instance,
                Instant.now(),
                traceId,
                null,
                null,
                null
        );
    }

    public static ErrorResponse internal(String detail, String instance, String traceId) {
        return new ErrorResponse(
                500,
                PROBLEM_BASE + "internal-error",
                "Внутренняя ошибка сервера",
                detail,
                instance,
                Instant.now(),
                traceId,
                null,
                null,
                null
        );
    }
}
