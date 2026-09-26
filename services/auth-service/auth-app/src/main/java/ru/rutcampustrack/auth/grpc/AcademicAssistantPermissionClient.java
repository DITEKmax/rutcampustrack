package ru.rutcampustrack.auth.grpc;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.AssistantPermissionCheckRequest;
import ru.rutcampustrack.auth.session.SessionAdmissionException;

import java.util.concurrent.TimeUnit;

/** Reads the actor's current VIEW_STATS authority from Academic using signed Auth identity. */
@Component
public final class AcademicAssistantPermissionClient {

    private static final Metadata.Key<String> INTERNAL_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final String VIEW_STATS = "VIEW_STATS";

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    public boolean hasViewStatsPermission(String internalToken, long groupId) {
        if (internalToken == null || internalToken.isBlank() || groupId <= 0) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
        }
        Metadata metadata = new Metadata();
        metadata.put(INTERNAL_TOKEN, internalToken);
        try {
            return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .checkAssistantPermission(AssistantPermissionCheckRequest.newBuilder()
                            .setGroupId(groupId)
                            .setPermission(VIEW_STATS)
                            .build())
                    .getAllowed();
        } catch (StatusRuntimeException error) {
            throw new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE, error);
        }
    }
}
