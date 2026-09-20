package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

@GrpcGlobalServerInterceptor
public class StudentGrpcIdentityInterceptor implements ServerInterceptor {

    static final String STUDENT_SERVICE = "rutcampustrack.attendance.AttendanceStudentGrpcService";
    static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final InternalJwtValidator validator;

    public StudentGrpcIdentityInterceptor(InternalJwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        if (!STUDENT_SERVICE.equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        try {
            InternalJwtClaims claims = validator.validate(headers.get(INTERNAL_TOKEN));
            Context context = Context.current().withValue(StudentGrpcIdentity.CLAIMS, claims);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (InternalJwtException error) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal identity"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
    }
}
