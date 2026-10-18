package ru.rutcampustrack.academic.security;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.concurrent.TimeUnit;

/** Live Auth admission before ordinary HTTP work, never inside a local transaction or cached. */
@Component
public class AcademicLiveSessionClient {
    @GrpcClient("auth-service")
    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub stub;

    private final String token;
    private final String authority;

    public AcademicLiveSessionClient(
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.token = token;
        this.authority = authority;
    }

    public AdminAccessProfile admit(InternalJwtClaims actor) {
        var admitted = admitCurrent(actor);
        var profile = admitted.getAdminProfile();
        return new AdminAccessProfile(
                actor.userId(),
                profile.getAssigned(),
                profile.getMode(),
                profile.getPermissionsList(),
                profile.getVersion());
    }

    /**
     * Exact current session metadata for projection; never inferred from caller claims or grants.
     */
    public LiveSessionAuthority admitProjection(InternalJwtClaims actor) {
        var admitted = admitCurrent(actor);
        if (!admitted.hasActiveRoleGrantId()
                || admitted.getActiveRoleGrantId() <= 0
                || admitted.getRefreshExpiresAt().isBlank()
                || admitted.hasRevokedAt()) {
            throw unavailable();
        }
        try {
            java.time.Instant.parse(admitted.getRefreshExpiresAt());
        } catch (java.time.DateTimeException malformed) {
            throw unavailable();
        }
        return admitted;
    }

    private LiveSessionAuthority admitCurrent(InternalJwtClaims actor) {
        if (actor == null
                || actor.sessionId() == null
                || actor.userId() <= 0
                || actor.rolesVersion() <= 0
                || actor.sessionVersion() <= 0
                || stub == null
                || TransactionSynchronizationManager.isActualTransactionActive()
                || authority == null
                || authority.isBlank()) throw unavailable();
        var identity =
                StoredSessionIdentity.newBuilder()
                        .setUserId(actor.userId())
                        .setSessionId(actor.sessionId().toString())
                        .setSessionVersion(actor.sessionVersion())
                        .setRolesVersion(actor.rolesVersion())
                        .setRole(actor.role())
                        .setStatus(actor.status())
                        .setIsHeadman(actor.isHeadman())
                        .setReadOnly(actor.readOnly());
        if (actor.groupId() != null) identity.setGroupId(actor.groupId());
        var requested = identity.build();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AcademicLiveSessionClient::unavailable);
        var admitted =
                stub.withCallCredentials(
                                ServiceTokenCallCredentials.forMethod(
                                        credential,
                                        authority,
                                        AuthSecurityCapabilityServiceGrpc
                                                .getAdmitSessionIdentityMethod()
                                                .getFullMethodName()))
                        .withDeadlineAfter(3, TimeUnit.SECONDS)
                        .admitSessionIdentity(requested);
        if (!admitted.getIdentity().equals(requested) || admitted.getInternalToken().isBlank())
            throw unavailable();
        var profile = admitted.getAdminProfile();
        if (profile.getAssigned() && profile.getVersion() <= 0) throw unavailable();
        return admitted;
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("current Auth session admission unavailable")
                .asRuntimeException();
    }
}
