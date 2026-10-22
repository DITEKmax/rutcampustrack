package ru.rutcampustrack.academic.subjectdeletion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SubjectDeletionController.class)
public class SubjectDeletionExceptionHandler {
    private static final Logger log =
            LoggerFactory.getLogger(SubjectDeletionExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> failure(Exception error) {
        int status = 503;
        if (error instanceof ResponseStatusException rejected)
            status = rejected.getStatusCode().value();
        else if (error instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || error
                        instanceof
                        org.springframework.http.converter.HttpMessageNotReadableException
                || error
                        instanceof
                        org.springframework.web.method.annotation
                                .MethodArgumentTypeMismatchException
                || error instanceof jakarta.validation.ConstraintViolationException) status = 400;
        else if (error instanceof org.springframework.web.bind.ServletRequestBindingException
                || error
                        instanceof
                        org.springframework.web.method.annotation.HandlerMethodValidationException)
            status = 400;
        else if (error instanceof ru.rutcampustrack.academic.exception.AccessDeniedException)
            status = 403;
        else if (error instanceof ru.rutcampustrack.academic.exception.ConflictException)
            status = 409;
        var body =
                ProblemDetail.forStatusAndDetail(
                        HttpStatusCode.valueOf(status), "Операция удаления предмета не завершена");
        if (error instanceof SubjectDeletionUnavailableException unavailable) {
            body.setInstance(URI.create("about:blank"));
            body.setProperty(
                    "extras",
                    Map.of(
                            "code", "SUBJECT_DELETION_UNAVAILABLE",
                            "phase", unavailable.phase().name(),
                            "reason", unavailable.failureReason().name(),
                            "rpcStatus", unavailable.rpcStatus().name()));
            log.warn(
                    "Subject deletion unavailable phase={} reason={} rpcStatus={}",
                    unavailable.phase(),
                    unavailable.failureReason(),
                    unavailable.rpcStatus());
        }
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
