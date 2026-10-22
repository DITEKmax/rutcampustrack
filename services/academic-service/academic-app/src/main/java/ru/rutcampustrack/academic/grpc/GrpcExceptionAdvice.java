package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;

@GrpcAdvice
public class GrpcExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(GrpcExceptionAdvice.class);

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleNotFound(ResourceNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(HistoricalMembershipException.class)
    public Status handleHistoricalMembership(HistoricalMembershipException e) {
        Status status =
                switch (e.code()) {
                    case INVALID_ARGUMENT -> Status.INVALID_ARGUMENT;
                    case NOT_FOUND -> Status.NOT_FOUND;
                    case FAILED_PRECONDITION, UNSUPPORTED_MUTATION -> Status.FAILED_PRECONDITION;
                };
        return status.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleBadRequest(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleInternal(Exception e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause
                    instanceof
                    ru.rutcampustrack.academic.exception.UnknownAssignmentCodeException) {
                return Status.FAILED_PRECONDITION.withDescription(
                        "Assignment state is incompatible with this operation");
            }
        }
        if (e instanceof io.grpc.StatusRuntimeException failure
                && failure.getStatus().getCode() == Status.Code.UNAVAILABLE) {
            String description = failure.getStatus().getDescription();
            if ("group identity operation is pending".equals(description)
                    || "identity operation publication is pending".equals(description)) {
                return Status.UNAVAILABLE.withDescription(
                        "Academic identity read is temporarily unavailable");
            }
        }
        log.error("gRPC internal error", e);
        return Status.INTERNAL.withDescription("Internal server error").withCause(e);
    }
}
