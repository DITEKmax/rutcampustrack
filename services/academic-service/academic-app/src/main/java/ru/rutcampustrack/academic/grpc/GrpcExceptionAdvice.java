package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;

@GrpcAdvice
public class GrpcExceptionAdvice {

    @GrpcExceptionHandler(ResourceNotFoundException.class)
    public Status handleNotFound(ResourceNotFoundException e) {
        return Status.NOT_FOUND.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(IllegalArgumentException.class)
    public Status handleBadRequest(IllegalArgumentException e) {
        return Status.INVALID_ARGUMENT.withDescription(e.getMessage()).withCause(e);
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleInternal(Exception e) {
        return Status.INTERNAL.withDescription("Internal server error").withCause(e);
    }
}
