package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

@GrpcGlobalServerInterceptor
public final class AdminActivityGrpcIdentityInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final InternalJwtValidator validator;

    public AdminActivityGrpcIdentityInterceptor(InternalJwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!AttendanceAdminActivityGrpcServiceGrpc.SERVICE_NAME.equals(
                call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        try {
            String token = headers.get(TOKEN);
            var claims = validator.validate(token);
            return Contexts.interceptCall(
                    Context.current()
                            .withValue(AdminActivityGrpcIdentity.CLAIMS, claims)
                            .withValue(AdminActivityGrpcIdentity.TOKEN, token),
                    call,
                    headers,
                    next);
        } catch (InternalJwtException invalid) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid internal identity"),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
