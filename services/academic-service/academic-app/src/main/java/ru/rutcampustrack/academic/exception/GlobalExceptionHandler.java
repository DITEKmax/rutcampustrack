package ru.rutcampustrack.academic.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.academic.contract.exception.ErrorResponse;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralized exception handler — RFC 7807 Problem Details for all exception types.
 * Controllers only throw exceptions; this class maps them to proper responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String BASE_URI = "https://api.rutcampustrack.ru/problems/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex,
                                                        HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                404,
                BASE_URI + "resource-not-found",
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
                403,
                BASE_URI + "access-denied",
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
                400,
                BASE_URI + "bad-request",
                "Некорректный запрос",
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
                409,
                BASE_URI + "conflict",
                "Конфликт данных",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .filter(error -> error instanceof FieldError)
                .map(error -> {
                    FieldError fe = (FieldError) error;
                    return new ErrorResponse.FieldError(
                            fe.getField(),
                            fe.getRejectedValue(),
                            fe.getDefaultMessage()
                    );
                })
                .collect(Collectors.toList());

        ErrorResponse body = new ErrorResponse(
                400,
                BASE_URI + "validation-error",
                "Ошибка валидации",
                "Одно или несколько полей содержат некорректные значения",
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
                500,
                BASE_URI + "internal-server-error",
                "Внутренняя ошибка сервера",
                ex.getMessage(),
                request.getRequestURI(),
                Instant.now(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
