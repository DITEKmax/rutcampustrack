package ru.rutcampustrack.shared.security.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Safe flat RFC9457 error; contains no SQL, credentials or bearer material. */
public final class AdminAccessException extends ErrorResponseException {
    public AdminAccessException(HttpStatus status, String code) {
        this(status, code, null);
    }

    public AdminAccessException(HttpStatus status, String code, AdminAccessProfile current) {
        super(status, ProblemDetail.forStatusAndDetail(status, code), null);
        getBody().setProperty("code", code);
        if (current != null) getBody().setProperty("currentProfile", current);
    }

    public static AdminAccessException invalid() {
        return new AdminAccessException(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_ACCESS");
    }

    public static AdminAccessException denied() {
        return new AdminAccessException(HttpStatus.FORBIDDEN, "ADMIN_PERMISSION_DENIED");
    }

    public static AdminAccessException fullRequired() {
        return new AdminAccessException(HttpStatus.FORBIDDEN, "ADMIN_FULL_REQUIRED");
    }

    public static AdminAccessException unavailable() {
        return new AdminAccessException(
                HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_AUTHORITY_UNAVAILABLE");
    }
}
