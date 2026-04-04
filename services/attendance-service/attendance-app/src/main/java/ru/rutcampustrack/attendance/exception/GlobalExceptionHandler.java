package ru.rutcampustrack.attendance.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.attendance.contract.exception.ErrorResponse;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handler returning RFC 7807 Problem Details responses.
 * Controllers only throw exceptions — this handler maps them to HTTP responses.
 * Includes MongoDB-specific (DuplicateKeyException) and gRPC-specific (service unavailable) handlers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://api.rutcampustrack.ru/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                         HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                PROBLEM_BASE + "resource-not-found",
                "Ресурс не найден",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                PROBLEM_BASE + "access-denied",
                "Доступ запрещён",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex,
                                                           HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                PROBLEM_BASE + "bad-request",
                "Неверный запрос",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex,
                                                         HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                PROBLEM_BASE + "conflict",
                "Конфликт данных",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex,
                                                             HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                PROBLEM_BASE + "duplicate-key",
                "Запись уже существует",
                ex.getMostSpecificCause().getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler({ScheduleServiceUnavailableException.class, AcademicServiceUnavailableException.class})
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(RuntimeException ex,
                                                                    HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                PROBLEM_BASE + "service-unavailable",
                "Сервис недоступен",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getRejectedValue(),
                        fe.getDefaultMessage()
                ))
                .collect(Collectors.toList());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                PROBLEM_BASE + "validation-failed",
                "Ошибка валидации",
                "Одно или несколько полей не прошли проверку",
                request.getRequestURI(),
                Instant.now(),
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex,
                                                        HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                PROBLEM_BASE + "internal-error",
                "Внутренняя ошибка сервера",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
