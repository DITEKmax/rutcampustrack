package ru.rutcampustrack.shared.web.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Элемент массива {@code invalidParams[]} в {@link ErrorResponse}
 * (RFC 9457, раздел «Extension Members»). Описывает одно поле, не прошедшее
 * валидацию.
 *
 * @param name          имя DTO-поля (например {@code "displayName"}).
 * @param reason        сообщение об ошибке валидации (локализованное).
 * @param rejectedValue отклонённое значение (может быть {@code null}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvalidParam(
        String name,
        String reason,
        Object rejectedValue
) {
}
