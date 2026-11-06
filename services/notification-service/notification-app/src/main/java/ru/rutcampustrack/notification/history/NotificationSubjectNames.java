package ru.rutcampustrack.notification.history;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.SubjectsByIdsRequest;

/** Optional names for exact subject IDs from already authorized inbox documents. */
@Component
class NotificationSubjectNames {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationSubjectNames.class);
    private static final Metadata.Key<String> GRPC_SECRET =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    private final String sharedSecret;

    NotificationSubjectNames(@Value("${grpc.auth.secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    Map<Long, String> lookup(Set<Long> subjectIds) {
        if (subjectIds.isEmpty() || sharedSecret.isBlank()) return Map.of();
        Metadata headers = new Metadata();
        headers.put(GRPC_SECRET, sharedSecret);
        try {
            var response =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .withDeadlineAfter(800, TimeUnit.MILLISECONDS)
                            .getSubjectsByIds(
                                    SubjectsByIdsRequest.newBuilder()
                                            .addAllSubjectIds(subjectIds)
                                            .build());
            Map<Long, String> names = new HashMap<>();
            for (var subject : response.getSubjectsList()) {
                if (!subjectIds.contains(subject.getSubjectId())) continue;
                if (names.putIfAbsent(subject.getSubjectId(), subject.getSubjectName()) != null) {
                    LOG.warn("Academic returned duplicate subject IDs for notification history");
                    return Map.of();
                }
            }
            return names;
        } catch (StatusRuntimeException unavailable) {
            LOG.warn("Academic subject names unavailable for notification history ({})",
                    unavailable.getStatus().getCode());
            return Map.of();
        }
    }
}
