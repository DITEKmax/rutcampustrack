package ru.rutcampustrack.schedule.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

@Component
public class AcademicAdminPermissionClient {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public void requireFull(String token) {
        if (token == null || token.isBlank()) throw AdminAccessException.denied();
        Metadata metadata = new Metadata();
        metadata.put(TOKEN, token);
        try {
            if (!stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .checkAdminPermission(
                            AdminPermissionCheckRequest.newBuilder().setFullOnly(true).build())
                    .getAllowed()) throw AdminAccessException.fullRequired();
        } catch (StatusRuntimeException failure) {
            if (failure.getStatus().getCode() == Status.Code.UNAUTHENTICATED)
                throw AdminAccessException.denied();
            throw AdminAccessException.unavailable();
        }
    }
}
