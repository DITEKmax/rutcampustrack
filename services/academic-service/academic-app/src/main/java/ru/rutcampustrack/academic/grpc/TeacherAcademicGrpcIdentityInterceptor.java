package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import ru.rutcampustrack.teacher.grpc.TeacherAcademicReadServiceGrpc;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

/** Authenticates only the dedicated teacher academic read service. */
@GrpcGlobalServerInterceptor
public final class TeacherAcademicGrpcIdentityInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final InternalJwtValidator validator;

    public TeacherAcademicGrpcIdentityInterceptor(InternalJwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        if (!TeacherAcademicReadServiceGrpc.SERVICE_NAME
                .equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        try {
            InternalJwtClaims claims = validator.validate(headers.get(INTERNAL_TOKEN));
            Context context = Context.current().withValue(TeacherAcademicGrpcIdentity.CLAIMS, claims);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (InternalJwtException error) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal identity"),
                    new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }
}
