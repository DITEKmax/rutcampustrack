package ru.rutcampustrack.schedule.grpc;

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

/**
 * Authenticates the Academic user identity only for the binding RPCs.
 * The shared-secret interceptor remains responsible for service identity, and
 * this interceptor independently validates the signed user token.  No caller
 * supplied actor id is accepted because it would make the binding owner
 * mutable by the transport caller.
 */
@GrpcGlobalServerInterceptor
public class HomeworkBindingActorInterceptor implements ServerInterceptor {

    static final String RESERVE_METHOD_NAME =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ReserveHomeworkBinding";
    static final String CONFIRM_METHOD_NAME =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ConfirmHomeworkBinding";
    static final String ARCHIVE_METHOD_NAME =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ArchiveHomeworkBinding";
    static final String GET_METHOD_NAME =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetHomeworkBindings";
    static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final InternalJwtValidator validator;

    public HomeworkBindingActorInterceptor(InternalJwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
        ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        if (!RESERVE_METHOD_NAME.equals(method)
                && !CONFIRM_METHOD_NAME.equals(method)
                && !ARCHIVE_METHOD_NAME.equals(method)
                && !GET_METHOD_NAME.equals(method)
                && !(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetHomeworkBinding").equals(method)
                && !(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/MoveHomeworkBinding").equals(method)) {
            return next.startCall(call, headers);
        }

        try {
            InternalJwtClaims claims = validator.validate(headers.get(INTERNAL_TOKEN));
            Context context = Context.current().withValue(HomeworkBindingActorContext.CLAIMS, claims);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (InternalJwtException error) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal identity"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
