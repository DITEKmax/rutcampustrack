package ru.rutcampustrack.notification.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.shared.web.exception.ErrorResponse;

import java.time.Instant;

/**
 * Доменные @ExceptionHandler notification-service.
 *
 * <p>Shared-web {@code GlobalExceptionHandler} обрабатывает стандартные
 * Spring-MVC исключения (Order=LOWEST_PRECEDENCE). Этот advice сидит
 * выше по приоритету и ловит ТОЛЬКО доменные исключения сервиса
 * (например {@link AccessDeniedException} из {@code security/}).
 *
 * <p>Shared-web handler ловит {@code org.springframework.security.access.AccessDeniedException}
 * (Spring Security). А {@code ru.rutcampustrack.notification.exception.AccessDeniedException}
 * — наш доменный класс, Spring shared handler его не знает — нужен свой.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                ErrorResponse.PROBLEM_BASE + "access-denied",
                "Доступ запрещён",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                MDC.get("traceId"),
                null,
                null,
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
