package ru.rutcampustrack.mobilebff.error;

import org.springframework.http.HttpStatus;
import ru.rutcampustrack.mobilebff.contract.model.StudentApiModels.ProblemCode;

import java.time.Instant;

public final class MobileBffException extends RuntimeException {
    private final HttpStatus status;
    private final ProblemCode code;
    private final Instant retryAt;

    public MobileBffException(HttpStatus status, ProblemCode code, String message) {
        this(status, code, message, null);
    }

    public MobileBffException(HttpStatus status, ProblemCode code, String message, Instant retryAt) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAt = retryAt;
    }

    public HttpStatus status() { return status; }
    public ProblemCode code() { return code; }
    public Instant retryAt() { return retryAt; }
}
