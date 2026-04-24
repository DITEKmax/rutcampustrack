package ru.rutcampustrack.shared.web.api.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Описание одной ошибки валидации поля DTO. Элемент массива
 * {@code fieldErrors[]} в {@link ErrorResponse} (RFC 9457, раздел
 * «Extension Members»).
 *
 * <p>M11 G0: единственный top-level {@code FieldError} во всём backend —
 * заменяет inner record'ы из 3 {@code *-api-contract}/ErrorResponse
 * (academic/schedule/attendance) и {@code InvalidParam} из shared-web.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ошибка валидации одного поля DTO (RFC 9457 Extension Member)")
public record FieldError(

        @Schema(description = "Имя поля DTO", example = "displayName")
        String field,

        @Schema(description = "Отклонённое значение", example = "")
        Object rejectedValue,

        @Schema(description = "Локализованное сообщение об ошибке",
                example = "Имя не может быть пустым")
        String message
) {
}
