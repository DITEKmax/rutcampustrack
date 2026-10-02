package ru.rutcampustrack.auth.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.auth.controller.QrLoginController;
import ru.rutcampustrack.auth.qr.QrLoginException;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import java.time.Instant;
import java.util.Map;

/** Never reflect rejected capabilities or raw parser/validation exception text. */
@RestControllerAdvice(assignableTypes=QrLoginController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class QrLoginExceptionHandler {
    @ExceptionHandler(QrLoginException.class)
    public ResponseEntity<ErrorResponse> denied(QrLoginException error,HttpServletRequest request) {
        HttpStatus status=switch (error.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case EXPIRED -> HttpStatus.GONE;
            case NOT_READY,ALREADY_DECIDED -> HttpStatus.CONFLICT;
            case SOURCE_DENIED,REPLAY_DENIED -> HttpStatus.UNAUTHORIZED;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return response(status,error.code().name(),request,error.retryAfter());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(HttpServletRequest request) { return response(HttpStatus.BAD_REQUEST,"INVALID_REQUEST",request,0); }
    private ResponseEntity<ErrorResponse> response(HttpStatus status,String code,HttpServletRequest request,long retryAfter) {
        ErrorResponse body=new ErrorResponse(status.value(),ErrorResponse.PROBLEM_BASE+"qr-login-failed",
                "Запрос QR-входа отклонён","Запрос не может быть выполнен",request.getRequestURI(),Instant.now(),
                MDC.get("traceId"),null,null,Map.of("code",code));
        var builder=ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (retryAfter>0) builder.header(HttpHeaders.RETRY_AFTER,Long.toString(retryAfter));
        return builder.body(body);
    }
}
