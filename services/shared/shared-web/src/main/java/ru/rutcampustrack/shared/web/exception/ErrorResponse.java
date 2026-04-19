package ru.rutcampustrack.shared.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Стандартный формат ошибки API по RFC 9457 (обновлённый RFC 7807) Problem Details.
 *
 * <p>Используется {@link GlobalExceptionHandler} и сервисными {@code @RestControllerAdvice}.
 * Поля {@code field} и {@code extras} — non-standard extensions для BUG-006-2
 * (conflict-field тэгирование) и каскадного удаления (scheduleItemsCount и т.п.).
 *
 * @param status         HTTP статус код.
 * @param type           URI типа ошибки.
 * @param title          краткое описание.
 * @param detail         детальное описание.
 * @param instance       URI запроса.
 * @param timestamp      время возникновения (UTC).
 * @param traceId        correlation id из MDC (P2-3/1).
 * @param invalidParams  ошибки валидации полей (для 400).
 * @param field          имя DTO-поля, вызвавшего конфликт (BUG-006-2).
 * @param extras         дополнительные данные для клиента (каскадное удаление и др.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String type,
        String title,
        String detail,
        String instance,
        Instant timestamp,
        String traceId,
        List<InvalidParam> invalidParams,
        String field,
        Map<String, Object> extras
) {

    public static final String PROBLEM_BASE = "https://api.rutcampustrack.ru/problems/";

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
