package ru.rutcampustrack.mobilebff.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.MobileProblemDetails;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RestControllerAdvice
public class MobileProblemHandler {
    private final Clock clock;

    public MobileProblemHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MobileBffException.class)
    ResponseEntity<MobileProblemDetails> handle(MobileBffException error, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        if (error.status() == HttpStatus.TOO_MANY_REQUESTS && error.retryAt() != null) {
            long seconds = Math.max(0, (long) Math.ceil(
                    Duration.between(clock.instant(), error.retryAt()).toMillis() / 1000.0));
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return new ResponseEntity<>(problem(error.status(), error.code(), error.getMessage(),
                error.retryAt(), request), headers, error.status());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<MobileProblemDetails> invalid(Exception error, HttpServletRequest request) {
        ProblemCode code = error.getMessage() != null && error.getMessage().contains("idempotencyKey")
                ? ProblemCode.INVALID_IDEMPOTENCY_KEY : ProblemCode.INVALID_REQUEST;
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, code,
                "Запрос не прошёл проверку", null, request));
    }

    private MobileProblemDetails problem(HttpStatus status, ProblemCode code, String detail,
                                         Instant retryAt, HttpServletRequest request) {
        return new MobileProblemDetails(
                status.value(), URI.create("urn:rct:problem:" + code.name().toLowerCase().replace('_', '-')),
                status.getReasonPhrase(), detail, URI.create(request.getRequestURI()), clock.instant(),
                null, code, retryAt, null);
    }
}
