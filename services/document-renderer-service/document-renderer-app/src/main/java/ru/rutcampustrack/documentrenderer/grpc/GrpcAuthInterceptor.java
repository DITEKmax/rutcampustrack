package ru.rutcampustrack.documentrenderer.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;

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
