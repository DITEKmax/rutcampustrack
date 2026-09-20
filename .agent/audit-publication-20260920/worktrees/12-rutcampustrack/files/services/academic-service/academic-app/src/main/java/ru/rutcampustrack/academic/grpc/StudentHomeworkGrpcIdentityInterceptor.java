package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/**
 * Authenticates only the student homework mutation RPC with the same signed
 * Internal JWT used at the HTTP BFF and attendance gRPC student boundary.
 * Existing Academic RPCs retain their established shared-secret contract.
 */
@GrpcGlobalServerInterceptor
public class StudentHomeworkGrpcIdentityInterceptor implements ServerInterceptor {
    static final String METHOD_NAME = AcademicGrpcServiceGrpc.SERVICE_NAME + "/SetHomeworkCompletion";
    static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final InternalJwtValidator validator;

    public StudentHomeworkGrpcIdentityInterceptor(InternalJwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!METHOD_NAME.equals(call.getMethodDescriptor().getFullMethodName())) {
            return next.startCall(call, headers);
        }
        try {
            InternalJwtClaims claims = validator.validate(headers.get(INTERNAL_TOKEN));
            Context context = Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (InternalJwtException error) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal identity"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }
}
