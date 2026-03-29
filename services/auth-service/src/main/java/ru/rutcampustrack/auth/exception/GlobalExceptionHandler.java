package ru.rutcampustrack.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.auth.dto.ErrorResponse;

import java.time.Instant;
import java.util.stream.Collectors;
import ru.rutcampustrack.auth.exception.OtpExpiredException;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        "about:blank",
                        "Unauthorized",
                        HttpStatus.UNAUTHORIZED.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ErrorResponse> handleTokenRefresh(
            TokenRefreshException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        "about:blank",
                        "Token Refresh Failed",
                        HttpStatus.UNAUTHORIZED.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "about:blank",
                        "Validation Error",
                        HttpStatus.BAD_REQUEST.value(),
                        detail,
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(
            OtpExpiredException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(
                        "about:blank",
                        "OTP Verification Failed",
                        HttpStatus.UNAUTHORIZED.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleOtpRateLimit(
            OtpRateLimitException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(
                        "about:blank",
                        "Rate Limited",
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "about:blank",
                        "Internal Server Error",
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        Instant.now()
                ));
    }
}
