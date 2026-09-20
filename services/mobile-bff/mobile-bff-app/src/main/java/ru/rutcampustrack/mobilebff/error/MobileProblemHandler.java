package ru.rutcampustrack.mobilebff.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl(CacheControl.noStore());
        if (error.status() == HttpStatus.TOO_MANY_REQUESTS && error.retryAt() != null) {
            long seconds = Math.max(0, (long) Math.ceil(
                    Duration.between(clock.instant(), error.retryAt()).toMillis() / 1000.0));
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return new ResponseEntity<>(problem(error.status(), error.code(), error.getMessage(),
                error.retryAt(), request), headers, error.status());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MissingServletRequestPartException.class, MultipartException.class,
            HttpMediaTypeNotSupportedException.class, MissingPathVariableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<MobileProblemDetails> invalid(Exception error, HttpServletRequest request) {
        return badRequest(ProblemCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<MobileProblemDetails> tooLarge(MaxUploadSizeExceededException error,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(problem(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCode.PAYLOAD_TOO_LARGE,
                        "Размер запроса превышает допустимый предел", null, request));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<MobileProblemDetails> invalidMethodArgument(HandlerMethodValidationException error,
                                                               HttpServletRequest request) {
        ProblemCode code = error.getParameterValidationResults().stream()
                .map(result -> result.getMethodParameter().getParameterAnnotation(RequestHeader.class))
                .anyMatch(header -> header != null
                        && "Idempotency-Key".equalsIgnoreCase(
                                header.name().isEmpty() ? header.value() : header.name()))
                ? ProblemCode.INVALID_IDEMPOTENCY_KEY : ProblemCode.INVALID_REQUEST;
        return badRequest(code, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<MobileProblemDetails> missingHeader(MissingRequestHeaderException error,
                                                       HttpServletRequest request) {
        ProblemCode code = "Idempotency-Key".equalsIgnoreCase(error.getHeaderName())
                ? ProblemCode.INVALID_IDEMPOTENCY_KEY : ProblemCode.INVALID_REQUEST;
        return badRequest(code, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<MobileProblemDetails> unreadableBody(HttpMessageNotReadableException error,
                                                        HttpServletRequest request) {
        return badRequest(ProblemCode.INVALID_REQUEST, request);
    }

    private ResponseEntity<MobileProblemDetails> badRequest(ProblemCode code, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(problem(HttpStatus.BAD_REQUEST, code, "Запрос не прошёл проверку", null, request));
    }

    private MobileProblemDetails problem(HttpStatus status, ProblemCode code, String detail,
                                         Instant retryAt, HttpServletRequest request) {
        return new MobileProblemDetails(
                status.value(), URI.create("urn:rct:problem:" + code.name().toLowerCase().replace('_', '-')),
                status.getReasonPhrase(), detail, URI.create(request.getRequestURI()), clock.instant(),
                null, code, retryAt, null);
    }
}
