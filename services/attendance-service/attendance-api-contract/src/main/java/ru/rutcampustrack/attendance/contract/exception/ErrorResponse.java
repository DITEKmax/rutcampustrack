package ru.rutcampustrack.attendance.contract.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

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

        @Schema(description = "Детальное описание", example = "Запись с id=99 не найдена")
        String detail,

        @Schema(description = "URI запроса, вызвавшего ошибку", example = "/api/attendance/checkins/99")
        String instance,

        @Schema(description = "Время возникновения ошибки")
        Instant timestamp,

        @Schema(description = "Ошибки валидации полей (только для 400)")
        List<FieldError> fieldErrors
) {

    public record FieldError(

            @Schema(description = "Имя поля", example = "lessonId")
            String field,

            @Schema(description = "Отклонённое значение", example = "")
            Object rejectedValue,

            @Schema(description = "Сообщение об ошибке", example = "Поле не может быть пустым")
            String message
    ) {}
}
