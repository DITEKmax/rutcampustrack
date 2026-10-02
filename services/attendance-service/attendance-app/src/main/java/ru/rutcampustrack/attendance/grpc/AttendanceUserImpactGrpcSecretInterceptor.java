package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@GrpcGlobalServerInterceptor
public class AttendanceUserImpactGrpcSecretInterceptor implements ServerInterceptor {
    @Value("${grpc.auth.secret:}") private String expectedSecret;
    @Override public <Q, S> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        if (!"rutcampustrack.attendance.AttendanceUserImpactGrpcService".equals(
                call.getMethodDescriptor().getServiceName())) return next.startCall(call, headers);
        String supplied = headers.get(Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER));
        if (expectedSecret == null || expectedSecret.isBlank() || supplied == null
                || !MessageDigest.isEqual(expectedSecret.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing gRPC secret"), new Metadata());
            return new ServerCall.Listener<>() { };
        }
        return next.startCall(call, headers);
    }
}
