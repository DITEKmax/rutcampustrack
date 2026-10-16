package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SubjectDeletionController.class)
public class SubjectDeletionExceptionHandler {
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
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatusCode.valueOf(status),
                                "Операция удаления предмета не завершена"));
    }
}
