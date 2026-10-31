package ru.rutcampustrack.academic.groupdeletion;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authsecurity.grpc.AuthSecurityCapabilityServiceGrpc;
import ru.rutcampustrack.groupdeletion.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.concurrent.TimeUnit;

@Component
public class GroupDeletionParticipants {
    @GrpcClient("auth-service")
    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub auth;

    @GrpcClient("schedule-service")
    private GroupDeletionParticipantGrpc.GroupDeletionParticipantBlockingStub schedule;

    @GrpcClient("attendance-service")
    private GroupDeletionParticipantGrpc.GroupDeletionParticipantBlockingStub attendance;

    private final String authToken,
            scheduleToken,
            attendanceToken,
            authAuthority,
            scheduleAuthority,
            attendanceAuthority;

    public GroupDeletionParticipants(
            @Value("${grpc.service-identity.academic-to-auth-token:}") String authToken,
            @Value("${grpc.service-identity.academic-to-schedule-token:}") String scheduleToken,
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String attendanceToken,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authAuthority,
            @Value("${grpc.client.schedule-service.security.authority-override:schedule-service}")
                    String scheduleAuthority,
            @Value(
                            "${grpc.client.attendance-service.security.authority-override:attendance-service}")
                    String attendanceAuthority) {
        this.authToken = authToken;
        this.scheduleToken = scheduleToken;
        this.attendanceToken = attendanceToken;
        this.authAuthority = authAuthority;
        this.scheduleAuthority = scheduleAuthority;
        this.attendanceAuthority = attendanceAuthority;
    }

    public GroupDeletionReceipt apply(int participant, GroupDeletionCommand command) {
        if (participant < 0 || participant > 2)
            throw new IllegalArgumentException("Unknown group deletion participant");
        if (participant == 0 && auth == null
                || participant == 1 && schedule == null
                || participant == 2 && attendance == null) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("RPC inside local transaction");
        String destination =
                participant == 0
                        ? "auth-service"
                        : participant == 1 ? "schedule-service" : "attendance-service";
        String token =
                participant == 0 ? authToken : participant == 1 ? scheduleToken : attendanceToken;
        String authority =
                participant == 0
                        ? authAuthority
                        : participant == 1 ? scheduleAuthority : attendanceAuthority;
        if (authority == null || authority.isBlank()) throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal(destination),
                                token)
                        .orElseThrow(GroupDeletionParticipants::unavailable);
        String method =
                participant == 0
                        ? AuthSecurityCapabilityServiceGrpc.getApplyGroupDeletionMethod()
                                .getFullMethodName()
                        : GroupDeletionParticipantGrpc.getApplyMethod().getFullMethodName();
        var credentials = ServiceTokenCallCredentials.forMethod(credential, authority, method);
        try {
            GroupDeletionReceipt receipt =
                    participant == 0
                            ? auth.withCallCredentials(credentials)
                                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                                    .applyGroupDeletion(command)
                            : (participant == 1 ? schedule : attendance)
                                    .withCallCredentials(credentials)
                                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                                    .apply(command);
            if (receipt.getCommitRejected()
                    && (participant != 0 || command.getAction() != GroupDeletionAction.COMMIT))
                throw unavailable();
            if (!receipt.getUnknownFields().asMap().isEmpty()
                    || !receipt.getCommand().equals(command)
                    || !receipt.getParticipantDigest().matches("[0-9a-f]{64}")
                    || receipt.getCountsMap().values().stream().anyMatch(v -> v < 0)
                    || command.getAction() != GroupDeletionAction.PREVIEW
                            && !receipt.getParticipantDigest()
                                    .equals(command.getParticipantDigest())) throw unavailable();
            return receipt;
        } catch (io.grpc.StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.FAILED_PRECONDITION
                    || e.getStatus().getCode() == io.grpc.Status.Code.ABORTED)
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "group_deletion_stale_preview");
            throw unavailable();
        }
    }

    private static RuntimeException unavailable() {
        return new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "group_deletion_dependency_unavailable");
    }
}
