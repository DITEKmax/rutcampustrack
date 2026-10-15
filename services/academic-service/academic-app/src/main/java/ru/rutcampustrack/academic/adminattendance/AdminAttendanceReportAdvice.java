package ru.rutcampustrack.academic.adminattendance;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Report errors never reflect selectors, rejected values, rows or dependency messages. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AdminAttendanceReportController.class)
public final class AdminAttendanceReportAdvice {
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> rejected(ErrorResponseException error) {
        return problem(error.getStatusCode());
    }

    @ExceptionHandler({
        org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.ServletRequestBindingException.class
    })
    public ResponseEntity<ProblemDetail> invalid(Exception error) {
        return problem(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ProblemDetail> unavailable(DataAccessException error) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatusCode status) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "Отчёт недоступен");
        body.setProperty(
                "code",
                switch (status.value()) {
                    case 400 -> "INVALID_REQUEST";
                    case 401 -> "UNAUTHENTICATED";
                    case 403 -> "ADMIN_PERMISSION_DENIED";
                    case 404 -> "RESOURCE_NOT_FOUND";
                    case 413 -> "PAYLOAD_TOO_LARGE";
                    case 409 -> "REPORT_DATA_CONFLICT";
                    default -> "DEPENDENCY_UNAVAILABLE";
                });
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
