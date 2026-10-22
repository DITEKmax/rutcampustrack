package ru.rutcampustrack.academic.identityoperation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;

@RestControllerAdvice
public class AcademicIdentityOperationHttpAdvice {
    @ExceptionHandler(AcademicIdentityPendingException.class)
    public ResponseEntity<IdentityOperationHttpModels.Operation> pending(
            AcademicIdentityPendingException pending) {
        var operation = pending.operation();
        return ResponseEntity.accepted()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("X-Operation-Id", operation.operationId().toString())
                .header(
                        "Location",
                        AcademicIdentityHttpOperations.location(operation.operationId()))
                .header("Retry-After", Integer.toString(operation.retryAfterSeconds()))
                .body(operation);
    }
}
