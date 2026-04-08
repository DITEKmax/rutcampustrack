package ru.rutcampustrack.academic.grpc;

import io.grpc.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * IMP-09: gRPC shared secret authentication interceptor.
 *
 * <p>Validates that inter-service gRPC calls include the correct shared secret
 * in the {@code x-grpc-secret} metadata header. Rejects unauthenticated calls
 * with UNAUTHENTICATED status.
 */
@GrpcGlobalServerInterceptor
public class GrpcAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.secret:#{null}}")
    private String expectedSecret;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        if (expectedSecret != null && !expectedSecret.isBlank()) {
            String provided = headers.get(SECRET_KEY);
            if (provided == null || !provided.equals(expectedSecret)) {
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing gRPC secret"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }

        return next.startCall(call, headers);
    }
}
