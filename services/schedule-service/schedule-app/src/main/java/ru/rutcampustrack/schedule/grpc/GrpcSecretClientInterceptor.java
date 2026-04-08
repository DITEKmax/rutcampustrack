package ru.rutcampustrack.schedule.grpc;

import io.grpc.*;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.springframework.beans.factory.annotation.Value;

/**
 * IMP-09: Attaches shared secret to outgoing gRPC calls.
 */
@GrpcGlobalClientInterceptor
public class GrpcSecretClientInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> SECRET_KEY =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Value("${grpc.auth.secret:}")
    private String secret;

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (secret != null && !secret.isBlank()) {
                    headers.put(SECRET_KEY, secret);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
