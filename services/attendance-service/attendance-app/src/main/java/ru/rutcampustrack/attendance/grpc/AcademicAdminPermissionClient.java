package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AdminPermissionCheckRequest;

/** A valid signed JWT alone never proves a current administrator capability. */
@Component
public class AcademicAdminPermissionClient {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public void requireDashboard(String token) {
        Metadata headers = new Metadata();
        headers.put(TOKEN, token);
        final boolean allowed;
        try {
            allowed =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                            .checkAdminPermission(
                                    AdminPermissionCheckRequest.newBuilder()
                                            .addPermissions("DASHBOARD_VIEW")
                                            .build())
                            .getAllowed();
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.UNAUTHENTICATED
                    || error.getStatus().getCode() == Status.Code.PERMISSION_DENIED) {
                throw Status.PERMISSION_DENIED
                        .withDescription("DASHBOARD_VIEW is required")
                        .asRuntimeException();
            }
            throw Status.UNAVAILABLE
                    .withDescription("Administrator authority unavailable")
                    .asRuntimeException();
        }
        if (!allowed)
            throw Status.PERMISSION_DENIED
                    .withDescription("DASHBOARD_VIEW is required")
                    .asRuntimeException();
    }
}
