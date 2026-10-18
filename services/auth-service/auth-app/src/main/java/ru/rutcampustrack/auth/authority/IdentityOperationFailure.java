package ru.rutcampustrack.auth.authority;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Closed machine code in a dedicated trailer; no SQL/cause/password is disclosed. */
final class IdentityOperationFailure {
    static final Metadata.Key<String> CODE =
            Metadata.Key.of("x-rct-error-code", Metadata.ASCII_STRING_MARSHALLER);

    private IdentityOperationFailure() {}

    static StatusRuntimeException error(Status status, String code) {
        Metadata trailers = new Metadata();
        trailers.put(CODE, code);
        return status.withDescription("Identity operation unavailable")
                .asRuntimeException(trailers);
    }
}
