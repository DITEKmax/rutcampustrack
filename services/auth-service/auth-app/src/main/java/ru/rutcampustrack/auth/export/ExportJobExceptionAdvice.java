package ru.rutcampustrack.auth.export;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.slf4j.MDC;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {ExportJobController.class, InternalExportJobController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExportJobExceptionAdvice {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> malformed(Exception failure, HttpServletRequest request) {
        return problem(400, "Некорректные параметры выгрузки", request);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> rejected(ResponseStatusException failure, HttpServletRequest request) {
        return problem(failure.getStatusCode().value(), "Операция с выгрузкой недоступна", request);
    }
    private static ResponseEntity<ErrorResponse> problem(int status, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ErrorResponse(status, ErrorResponse.PROBLEM_BASE + "export-unavailable", "Выгрузка недоступна",
                        detail, request.getRequestURI(), Instant.now(), MDC.get("traceId"), null, null, null));
    }
}
