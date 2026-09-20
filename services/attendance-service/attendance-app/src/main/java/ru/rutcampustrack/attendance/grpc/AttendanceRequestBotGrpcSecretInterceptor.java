package ru.rutcampustrack.attendance.grpc;

import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/** Fail-closed shared-secret boundary for the exact bot attachment service. */
@GrpcGlobalServerInterceptor
public class AttendanceRequestBotGrpcSecretInterceptor implements ServerInterceptor {
    static final String BOT_SERVICE = "rutcampustrack.attendance.AttendanceRequestBotGrpcService";
    static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.secret:}")
    private String expectedSecret;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!BOT_SERVICE.equals(call.getMethodDescriptor().getServiceName())) {
            return next.startCall(call, headers);
        }
        String provided = headers.get(SECRET_KEY);
        if (expectedSecret == null || expectedSecret.isBlank()
                || provided == null || !java.security.MessageDigest.isEqual(
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                expectedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing gRPC secret"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }
}
