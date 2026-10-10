package ru.rutcampustrack.academic.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Narrow fail-closed admission for notification authority; other RPCs keep their contract. */
@GrpcGlobalServerInterceptor
public final class NotificationAudienceServiceAdmission implements ServerInterceptor {
    private static final Context.Key<Boolean> ADMITTED = Context.key("notification-audience-admitted");
    private static final Metadata.Key<String> SECRET = Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);
    private final String expected;

    public NotificationAudienceServiceAdmission(@Value("${grpc.auth.secret:}") String expected) {
        this.expected = expected;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                               Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!call.getMethodDescriptor().getServiceName().equals(NotificationAudienceGrpcServiceGrpc.SERVICE_NAME))
            return next.startCall(call, headers);
        String supplied = headers.get(SECRET);
        if (expected == null || expected.isBlank() || supplied == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("notification service admission required"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(ADMITTED, true), call, headers, next);
    }

    static void require() {
        if (!Boolean.TRUE.equals(ADMITTED.get()))
            throw Status.UNAUTHENTICATED.withDescription("notification service admission required").asRuntimeException();
    }
}
