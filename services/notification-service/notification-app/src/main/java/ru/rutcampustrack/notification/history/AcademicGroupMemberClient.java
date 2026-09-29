package ru.rutcampustrack.notification.history;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.GroupMembersRequest;
import ru.rutcampustrack.academic.grpc.GroupMemberIdsAsOfRequest;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Reads the authoritative dated membership audience from Academic. */
@Component
public class AcademicGroupMemberClient {

    private static final Metadata.Key<String> GRPC_SECRET =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @GrpcClient("academic-service")
    private AcademicGrpcServiceGrpc.AcademicGrpcServiceBlockingStub stub;

    private final String sharedSecret;

    public AcademicGroupMemberClient(@Value("${grpc.auth.secret:}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    /**
     * Returns IDs only. A missing shared secret or an invalid response fails
     * closed; Academic errors propagate so the history transaction can roll
     * back its event claim and use the listener's bounded retry/DLQ policy.
     */
    public List<Long> getMemberUserIds(long groupId, LocalDate asOfDate) {
        if (groupId <= 0 || asOfDate == null) {
            throw new IllegalArgumentException("groupId and asOfDate are required");
        }
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException("Academic gRPC shared secret is not configured");
        }

        Metadata headers = new Metadata();
        headers.put(GRPC_SECRET, sharedSecret);
        GroupMemberIdsAsOfRequest request = GroupMemberIdsAsOfRequest.newBuilder()
                .setGroupId(groupId)
                .setAsOfDate(asOfDate.toString())
                .build();
        var response = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getGroupMemberIdsAsOf(request);
        if (response.getGroupId() != groupId || !asOfDate.toString().equals(response.getAsOfDate())) {
            throw new IllegalStateException("Academic returned a mismatched membership scope");
        }
        List<Long> ids = response.getUserIdsList();
        Set<Long> uniqueIds = new HashSet<>();
        for (Long userId : ids) {
            if (userId == null || userId <= 0 || !uniqueIds.add(userId)) {
                throw new IllegalStateException("Academic returned invalid member IDs");
            }
        }
        return List.copyOf(ids);
    }

    /** Returns the current headman IDs from Academic's current group-members snapshot. */
    public List<Long> getCurrentHeadmanUserIds(long groupId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive");
        }
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException("Academic gRPC shared secret is not configured");
        }

        Metadata headers = new Metadata();
        headers.put(GRPC_SECRET, sharedSecret);
        var request = GroupMembersRequest.newBuilder()
                .setGroupId(groupId)
                .build();
        var response = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .getGroupMembers(request);
        Set<Long> uniqueIds = new HashSet<>();
        for (var student : response.getStudentsList()) {
            if (!student.getIsHeadman()) {
                continue;
            }
            long userId = student.getUserId();
            if (userId <= 0 || !uniqueIds.add(userId)) {
                throw new IllegalStateException("Academic returned invalid current headman IDs");
            }
        }
        return List.copyOf(uniqueIds);
    }
}
