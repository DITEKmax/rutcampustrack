package ru.rutcampustrack.academic.contract.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Стандартный формат ошибки по RFC 7807 Problem Details.
 * Используется GlobalExceptionHandler во всех сервисах.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Стандартный формат ошибки API")
public record ErrorResponse(

        @Schema(description = "HTTP статус код", example = "404")
        int status,

        @Schema(description = "URI типа ошибки", example = "https://api.rutcampustrack.ru/problems/resource-not-found")
        String type,

        @Schema(description = "Краткое описание ошибки", example = "Ресурс не найден")
        String title,

        @Schema(description = "Детальное описание", example = "Группа с id=99 не найдена")
        String detail,

        @Schema(description = "URI запроса, вызвавшего ошибку", example = "/api/academic/groups/99")
        String instance,

        @Schema(description = "Время возникновения ошибки")
        Instant timestamp,

        @Schema(description = "Ошибки валидации полей (только для 400)")
        List<FieldError> fieldErrors,

        @Schema(description = "Имя поля DTO, вызвавшего конфликт (только для 409, BUG-006-2)",
                example = "login")
        String field,

        @Schema(description = "Дополнительные данные для клиента (например, счётчики каскадного удаления)",
                example = "{\"scheduleItemsCount\":3,\"nonPlannedLessonsCount\":12}")
        Map<String, Object> extras
) {

    /**
     * Backward-compatible 7-arg constructor — used by legacy call sites that do
     * not produce a conflict-field value. Delegates to the canonical 9-arg
     * constructor with {@code field=null, extras=null}.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp,
                         List<FieldError> fieldErrors) {
        this(status, type, title, detail, instance, timestamp, fieldErrors, null, null);
    }

    /**
     * Backward-compatible 8-arg constructor — preserves {@code field} while
     * leaving {@code extras=null}. Existing handlers keep compiling unchanged.
     */
    public ErrorResponse(int status,
                         String type,
                         String title,
                         String detail,
                         String instance,
                         Instant timestamp,
                         List<FieldError> fieldErrors,
                         String field) {
        this(status, type, title, detail, instance, timestamp, fieldErrors, field, null);
    }

    public record FieldError(

            @Schema(description = "Имя поля", example = "displayName")
            String field,

            @Schema(description = "Отклонённое значение", example = "")
            Object rejectedValue,

            @Schema(description = "Сообщение об ошибке", example = "Имя не может быть пустым")
            String message
    ) {}
}
