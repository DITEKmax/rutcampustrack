package ru.rutcampustrack.schedule.constructor;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConstructorExceptionHandler {
    @ExceptionHandler(ConstructorLifecycleException.class)
    public ResponseEntity<ErrorResponse> handle(
            ConstructorLifecycleException failure, HttpServletRequest request) {
        return ResponseEntity.status(failure.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(
                        new ErrorResponse(
                                failure.status(),
                                "https://api.rutcampustrack.ru/problems/constructor-lifecycle",
                                "Операция конструктора не завершена",
                                failure.getMessage(),
                                request.getRequestURI(),
                                Instant.now(),
                                org.slf4j.MDC.get("traceId"),
                                null,
                                null,
                                Map.of("code", failure.code())));
    }
}
