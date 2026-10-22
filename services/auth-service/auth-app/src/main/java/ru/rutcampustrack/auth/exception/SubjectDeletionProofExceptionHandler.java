package ru.rutcampustrack.auth.exception;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.auth.controller.InternalSubjectDeletionProofController;
import ru.rutcampustrack.auth.session.AuthSessionException;

/** Secret-bearing validation/database failures never expose rejected values or causes. */
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InternalSubjectDeletionProofController.class)
public class SubjectDeletionProofExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> failure(Exception error) {
        int status = 503;
        if (error instanceof ResponseStatusException rejected)
            status = rejected.getStatusCode().value();
        else if (error instanceof org.springframework.web.bind.MethodArgumentNotValidException
                || error
                        instanceof
                        org.springframework.http.converter.HttpMessageNotReadableException)
            status = 400;
        else if (error instanceof AuthSessionException session)
            status =
                    switch (session.code()) {
                        case AUTHORITY_UNAVAILABLE -> 503;
                        case INVALID_SESSION,
                                SESSION_NOT_FOUND,
                                SESSION_REVOKED,
                                REFRESH_REJECTED ->
                                401;
                        case SESSION_STATE_STALE, SESSION_VERSION_CONFLICT -> 409;
                        default -> 403;
                    };
        else if (error instanceof OtpRateLimitException) status = 429;
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatusCode.valueOf(status),
                                "Подтверждение удаления недоступно"));
    }
}
