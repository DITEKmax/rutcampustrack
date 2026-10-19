package ru.rutcampustrack.academic.subjectdeletion;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.util.Objects;

/** Closed PREVIEW diagnostics; the original exception and its data are never retained. */
final class SubjectDeletionUnavailableException extends ResponseStatusException {
    enum Phase {
        LOCAL_TARGET,
        LOCAL_SNAPSHOT,
        SCHEDULE_PREVIEW,
        ATTENDANCE_PREVIEW
    }

    enum Reason {
        IDENTITY_READ_FENCE,
        LOCAL_STORAGE,
        CREDENTIAL_CONFIGURATION,
        AUTHORITY_CONFIGURATION,
        RPC_FAILURE,
        RECEIPT_MISMATCH,
        UNEXPECTED
    }

    enum RpcStatus {
        NOT_APPLICABLE,
        OK,
        CANCELLED,
        UNKNOWN,
        INVALID_ARGUMENT,
        DEADLINE_EXCEEDED,
        NOT_FOUND,
        ALREADY_EXISTS,
        PERMISSION_DENIED,
        RESOURCE_EXHAUSTED,
        FAILED_PRECONDITION,
        ABORTED,
        OUT_OF_RANGE,
        UNIMPLEMENTED,
        INTERNAL,
        UNAVAILABLE,
        DATA_LOSS,
        UNAUTHENTICATED;

        static RpcStatus from(Status.Code code) {
            return valueOf(code.name());
        }
    }

    private final Phase phase;
    private final Reason failureReason;
    private final RpcStatus rpcStatus;

    SubjectDeletionUnavailableException(Phase phase, Reason reason, RpcStatus rpcStatus) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Subject deletion preview unavailable");
        this.phase = Objects.requireNonNull(phase);
        this.failureReason = Objects.requireNonNull(reason);
        this.rpcStatus = Objects.requireNonNull(rpcStatus);
    }

    Phase phase() {
        return phase;
    }

    Reason failureReason() {
        return failureReason;
    }

    RpcStatus rpcStatus() {
        return rpcStatus;
    }

    static RuntimeException local(Phase phase, RuntimeException error) {
        if (error instanceof SubjectDeletionUnavailableException
                || error instanceof DataIntegrityViolationException
                || error instanceof AccessDeniedException
                || error instanceof BadRequestException
                || error instanceof ConflictException
                || error instanceof ResourceNotFoundException) return error;
        if (error instanceof ErrorResponse rejected && rejected.getStatusCode().value() != 503)
            return error;
        if (error instanceof StatusRuntimeException rejected) {
            if (rejected.getStatus().getCode() != Status.Code.UNAVAILABLE) return error;
            return new SubjectDeletionUnavailableException(
                    phase, Reason.IDENTITY_READ_FENCE, RpcStatus.UNAVAILABLE);
        }
        return new SubjectDeletionUnavailableException(
                phase,
                error instanceof DataAccessException ? Reason.LOCAL_STORAGE : Reason.UNEXPECTED,
                RpcStatus.NOT_APPLICABLE);
    }
}
