package ru.rutcampustrack.academic.exportjobs;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class AuthReportSecurityClient implements ReportSecurityAuthority {
    @GrpcClient("auth-service")
    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub stub;

    private final String token;
    private final String authority;

    public AuthReportSecurityClient(
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.token = token;
        this.authority = authority;
    }

    @Override
    public Admission admit(StoredReportIdentity identity, boolean full, UUID jobId, UUID leaseId) {
        var request = request(identity, full);
        if (jobId != null) request.setOwnerResourceId(jobId.toString());
        if (leaseId != null) request.setLeaseId(leaseId.toString());
        var reply =
                directed(
                                AuthSecurityCapabilityServiceGrpc.getAdmitStoredSessionMethod()
                                        .getFullMethodName())
                        .admitStoredSession(request.build());
        return admission(reply);
    }

    @Override
    public Admission admit(
            StoredReportIdentity identity,
            boolean full,
            UUID jobId,
            UUID leaseId,
            long deadlineNanos) {
        var request = request(identity, full);
        if (jobId != null) request.setOwnerResourceId(jobId.toString());
        if (leaseId != null) request.setLeaseId(leaseId.toString());
        var reply =
                directed(
                                AuthSecurityCapabilityServiceGrpc.getAdmitStoredSessionMethod()
                                        .getFullMethodName(),
                                deadlineNanos)
                        .admitStoredSession(request.build());
        return admission(reply);
    }

    @Override
    public Admission admitPlan(
            StoredReportIdentity identity,
            boolean full,
            UUID jobId,
            UUID leaseId,
            String planDigest) {
        if (jobId == null
                || leaseId == null
                || planDigest == null
                || !planDigest.matches("[0-9a-f]{64}"))
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        var request =
                request(identity, full)
                        .setOwnerResourceId(jobId.toString())
                        .setLeaseId(leaseId.toString())
                        .setSelectorHash(planDigest);
        return admission(
                directed(
                                AuthSecurityCapabilityServiceGrpc.getAdmitStoredSessionMethod()
                                        .getFullMethodName())
                        .admitStoredSession(request.build()));
    }

    @Override
    public Capability issue(StoredReportIdentity identity, boolean full, UUID jobId) {
        var reply =
                directed(
                                AuthSecurityCapabilityServiceGrpc.getIssueReportCapabilityMethod()
                                        .getFullMethodName())
                        .issueReportCapability(
                                IssueReportCapabilityRequest.newBuilder()
                                        .setAuthority(request(identity, full))
                                        .setOwnerResourceId(jobId.toString())
                                        .build());
        return new Capability(reply.getTicket(), Instant.parse(reply.getExpiresAt()));
    }

    @Override
    public Redemption redeem(String ticket) {
        var reply =
                directed(
                                AuthSecurityCapabilityServiceGrpc.getRedeemReportCapabilityMethod()
                                        .getFullMethodName())
                        .redeemReportCapability(
                                RedeemReportCapabilityRequest.newBuilder()
                                        .setTicket(ticket)
                                        .build());
        return new Redemption(
                admission(reply.getAuthority()),
                reply.getReportKind(),
                reply.getSelectorHash(),
                UUID.fromString(reply.getOwnerResourceId()));
    }

    private static AdmitStoredSessionRequest.Builder request(
            StoredReportIdentity identity, boolean full) {
        return AdmitStoredSessionRequest.newBuilder()
                .setIdentity(wire(identity.actor()))
                .setReportKind(identity.report().kind().code())
                .setSelectorHash(identity.report().bindingHash())
                .setNotAfter(identity.expiresAt().toString())
                .setRequiresFull(full);
    }

    private static StoredSessionIdentity wire(InternalJwtClaims actor) {
        var value =
                StoredSessionIdentity.newBuilder()
                        .setUserId(actor.userId())
                        .setSessionId(actor.sessionId().toString())
                        .setSessionVersion(actor.sessionVersion())
                        .setRolesVersion(actor.rolesVersion())
                        .setRole(actor.role())
                        .setStatus(actor.status())
                        .setIsHeadman(actor.isHeadman())
                        .setReadOnly(actor.readOnly());
        if (actor.groupId() != null) value.setGroupId(actor.groupId());
        return value.build();
    }

    private static Admission admission(AdmittedReportAuthority reply) {
        var p = reply.getIdentity();
        var actor =
                new InternalJwtClaims(
                        p.getUserId(),
                        UUID.fromString(p.getSessionId()),
                        p.getSessionVersion(),
                        p.getRolesVersion(),
                        p.getRole(),
                        p.getStatus(),
                        p.hasGroupId() ? p.getGroupId() : null,
                        p.getIsHeadman(),
                        p.getReadOnly());
        return new Admission(
                actor,
                reply.getInternalToken(),
                Instant.parse(reply.getExpiresAt()),
                reply.hasAdminProfile()
                        && reply.getAdminProfile().getAssigned()
                        && "FULL".equals(reply.getAdminProfile().getMode()),
                reply.hasAdminProfile()
                        ? new ru.rutcampustrack.shared.security.admin.AdminAccessProfile(
                                actor.userId(),
                                reply.getAdminProfile().getAssigned(),
                                reply.getAdminProfile().getMode(),
                                reply.getAdminProfile().getPermissionsList(),
                                reply.getAdminProfile().getVersion())
                        : null);
    }

    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub directed(
            String method, long deadlineNanos) {
        if (deadlineNanos - System.nanoTime() <= 0) throw Status.UNAVAILABLE.asRuntimeException();
        var selected = directed(method);
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) throw Status.UNAVAILABLE.asRuntimeException();
        return selected.withDeadlineAfter(
                Math.min(remaining, TimeUnit.SECONDS.toNanos(3)), TimeUnit.NANOSECONDS);
    }

    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub directed(
            String method) {
        if (stub == null
                || TransactionSynchronizationManager.isActualTransactionActive()
                || authority == null
                || authority.isBlank()) throw Status.UNAVAILABLE.asRuntimeException();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(() -> Status.UNAVAILABLE.asRuntimeException());
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withDeadlineAfter(3, TimeUnit.SECONDS);
    }
}
