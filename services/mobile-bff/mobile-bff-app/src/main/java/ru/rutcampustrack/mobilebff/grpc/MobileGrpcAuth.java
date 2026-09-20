package ru.rutcampustrack.mobilebff.grpc;

import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.mobilebff.security.MobileRequestContext;

@Component
public class MobileGrpcAuth {
    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> GRPC_SECRET =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    private final MobileRequestContext context;
    private final String grpcSecret;

    public MobileGrpcAuth(MobileRequestContext context, @Value("${grpc.auth.secret:}") String grpcSecret) {
        this.context = context;
        this.grpcSecret = grpcSecret;
    }

    public <T extends AbstractStub<T>> T attach(T stub) {
        Metadata headers = new Metadata();
        headers.put(INTERNAL_TOKEN, context.token());
        if (grpcSecret != null && !grpcSecret.isBlank()) headers.put(GRPC_SECRET, grpcSecret);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }
}
