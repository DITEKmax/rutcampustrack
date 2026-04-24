package ru.rutcampustrack.shared.web.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Описание одной невалидной переменной запроса (query/path параметр).
 * RFC 9457 «Extension Members».
 *
 * <p>До M11: использовалось shared-web для всех validation ошибок.
 * После M11: для form-binding/path/query параметров. Для body DTO
 * валидаций предпочтителен {@link FieldError} (унификация с
 * академическим / schedule / attendance форматом, который ожидает
 * frontend).
 *
 * @param name          имя параметра (например {@code "groupId"}).
 * @param reason        сообщение об ошибке валидации (локализованное).
 * @param rejectedValue отклонённое значение (может быть {@code null}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Невалидный параметр запроса (path/query). " +
        "Для ошибок валидации полей body используйте FieldError.")
public record InvalidParam(

        @Schema(description = "Имя параметра", example = "groupId")
        String name,

        @Schema(description = "Причина ошибки", example = "Параметр обязателен")
        String reason,

        @Schema(description = "Отклонённое значение")
        Object rejectedValue
) {
}
