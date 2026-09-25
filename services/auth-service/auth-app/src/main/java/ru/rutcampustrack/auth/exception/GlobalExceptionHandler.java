package ru.rutcampustrack.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.auth.session.SessionAdmissionException;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.service.ReportDownloadTicketRateLimitException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

import java.time.Instant;
import java.util.Map;

/**
 * Domain-level exception handler для auth-service.
 *
 * <p>M11 G0.7: catch-all Spring MVC exceptions (validation/noHandler/
 * general) делегированы в shared-web GlobalExceptionHandler через
 * {@code @Order(LOWEST_PRECEDENCE)}. Auth-domain handler с
 * {@code @Order(HIGHEST_PRECEDENCE)} обрабатывает только auth-specific.
 *
 * <p>До M11 G0.7 auth использовал свой 6-полевой {@code auth/dto/ErrorResponse}
 * (без traceId / fieldErrors) с {@code type="about:blank"}. После
 * унификации — единый shared {@link ErrorResponse} (10 полей, RFC 9457
 * с traceId + правильным {@code type} URI).
 *
 * <p>Сохранены auth-specific:
 * <ul>
 *   <li>{@link InvalidCredentialsException} → 401</li>
 *   <li>{@link TokenRefreshException} → 401</li>
 *   <li>{@link OtpExpiredException} → 401</li>
 *   <li>{@link OtpRateLimitException} → 429</li>
 *   <li>{@link TmaValidationException} → 401 (Telegram Mini App)</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** MDC key для correlation id. */
    private static final String MDC_TRACE_ID = "traceId";

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "invalid-credentials",
                "Неверные учётные данные", ex.getMessage(), request);
    }

    @ExceptionHandler(TokenRefreshException.class)
    public ResponseEntity<ErrorResponse> handleTokenRefresh(
            TokenRefreshException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "token-refresh-failed",
                "Не удалось обновить токен", ex.getMessage(), request);
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(
            OtpExpiredException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "otp-verification-failed",
                "OTP истёк или неверен", ex.getMessage(), request);
    }

    @ExceptionHandler(OtpRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleOtpRateLimit(
            OtpRateLimitException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "rate-limit-exceeded",
                "Превышен лимит запросов OTP", ex.getMessage(), request);
    }

    @ExceptionHandler(TmaValidationException.class)
    public ResponseEntity<ErrorResponse> handleTmaValidation(
            TmaValidationException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "tma-validation-failed",
                "Telegram Mini App валидация не пройдена",
                ex.getMessage(), request);
    }

    @ExceptionHandler(SessionAdmissionException.class)
    public ResponseEntity<ErrorResponse> handleSessionAdmission(
            SessionAdmissionException ex,
            HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case INVALID_SESSION, SESSION_REVOKED -> HttpStatus.UNAUTHORIZED;
            case ROLE_NOT_GRANTED, ROLE_NOT_SELECTABLE -> HttpStatus.FORBIDDEN;
            case SESSION_STATE_STALE -> HttpStatus.CONFLICT;
            case AUTHORITY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ErrorResponse body = new ErrorResponse(
                status.value(),
                ErrorResponse.PROBLEM_BASE + "session-admission-failed",
                "Session admission failed",
                "Session admission was denied",
                request.getRequestURI(),
                Instant.now(),
                MDC.get(MDC_TRACE_ID),
                null,
                null,
                Map.of("code", ex.code().name())
        );
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    @ExceptionHandler(ReportDownloadTicketRateLimitException.class)
    public ResponseEntity<ErrorResponse> handleReportDownloadTicketRateLimit(
            ReportDownloadTicketRateLimitException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "report-download-ticket-rate-limit-exceeded",
                "Request limit exceeded", "Retry after the current ticket window expires", request);
    }

    @ExceptionHandler(InvalidReportDownloadTicketRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReportDownloadTicketRequest(
            InvalidReportDownloadTicketRequestException ex,
            HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-report-download-ticket-request",
                "Invalid report selector", ex.getMessage(), request);
    }

    @ExceptionHandler(AuthSessionException.class)
    public ResponseEntity<ErrorResponse> handleAuthSession(
            AuthSessionException ex,
            HttpServletRequest request) {
        HttpStatus status = switch (ex.code()) {
            case PASSWORD_POLICY_VIOLATION, INVALID_CURSOR -> HttpStatus.BAD_REQUEST;
            case CURRENT_PASSWORD_INVALID -> HttpStatus.BAD_REQUEST;
            case INVALID_SESSION, SESSION_REVOKED, REFRESH_REJECTED -> HttpStatus.UNAUTHORIZED;
            case ROLE_NOT_GRANTED, ROLE_NOT_SELECTABLE, ROLE_READ_ONLY,
                    BOOTSTRAP_SCOPE_DENIED -> HttpStatus.FORBIDDEN;
            case SESSION_STATE_STALE, SESSION_VERSION_CONFLICT,
                    REFRESH_ALREADY_ROTATED -> HttpStatus.CONFLICT;
            case AUTHORITY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ErrorResponse body = new ErrorResponse(
                status.value(), ErrorResponse.PROBLEM_BASE + "auth-session-failed",
                "Authentication session request failed",
                "The authentication session request was denied",
                request.getRequestURI(), Instant.now(), MDC.get(MDC_TRACE_ID), null, null,
                Map.of("code", ex.code().name()));
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static ResponseEntity<ErrorResponse> problem(
            HttpStatus status,
            String problemType,
            String title,
            String detail,
            HttpServletRequest request) {
        String traceId = MDC.get(MDC_TRACE_ID);
        ErrorResponse body = new ErrorResponse(
                status.value(),
                ErrorResponse.PROBLEM_BASE + problemType,
                title,
                detail,
                request.getRequestURI(),
                Instant.now(),
                traceId,
                null,
                null,
                null);
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
