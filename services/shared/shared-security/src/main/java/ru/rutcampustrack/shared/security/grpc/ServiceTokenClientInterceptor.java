package ru.rutcampustrack.shared.security.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

/**
 * Narrow client adapter for a protected stub. It removes caller-supplied
 * service-token metadata before the scoped {@link ServiceTokenCallCredentials}
 * can add the one configured credential.
 */
public final class ServiceTokenClientInterceptor implements ClientInterceptor {

    private final ServiceTokenCallCredentials credentials;

    public ServiceTokenClientInterceptor(ServiceTokenCallCredentials credentials) {
        if (credentials == null) {
            throw new IllegalArgumentException("Service credentials are required");
        }
        this.credentials = credentials;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        CallOptions scopedOptions = credentials.appliesTo(method.getFullMethodName())
                ? callOptions.withCallCredentials(credentials)
                : callOptions;
        ClientCall<ReqT, RespT> delegate = next.newCall(
                method, scopedOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                if (headers != null) {
                    headers.discardAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
