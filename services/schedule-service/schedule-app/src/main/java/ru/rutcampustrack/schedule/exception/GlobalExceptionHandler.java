package ru.rutcampustrack.schedule.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.schedule.contract.exception.ErrorResponse;

import java.time.Instant;

/**
 * Centralized exception handler returning RFC 7807 Problem Details responses.
 * Controllers only throw exceptions -- this handler maps them to HTTP status codes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://api.rutcampustrack.ru/problems/";

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                PROBLEM_BASE + "access-denied",
                "Access denied",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex,
                                                        HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                PROBLEM_BASE + "internal-error",
                "Internal server error",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
