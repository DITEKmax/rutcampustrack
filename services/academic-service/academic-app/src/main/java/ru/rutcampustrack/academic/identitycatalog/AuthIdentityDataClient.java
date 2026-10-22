package ru.rutcampustrack.academic.identitycatalog;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bounded Auth data snapshots are service-admitted and never confer user authority. Explicit
 * CheckAdminAccess/credential methods independently admit the live actor in Auth.
 */
@Component
public class AuthIdentityDataClient {
    @GrpcClient("auth-service")
    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub stub;

    private final RequestContext context;
    private final String token;
    private final String authority;

    public AuthIdentityDataClient(
            RequestContext context,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.context = context;
        this.token = token;
        this.authority = authority;
    }

    public Map<Long, IdentityAuthoritySnapshot> read(
            Collection<Long> selected, long deadlineNanos) {
        return readServiceIdentity(selected, deadlineNanos);
    }

    /** Nonsecret machine snapshot only; no actor bearer, session admission or credentials. */
    public Map<Long, IdentityAuthoritySnapshot> readServiceIdentity(
            Collection<Long> selected, long deadlineNanos) {
        var ids = selected.stream().distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();
        if (ids.size() > 200 || ids.getFirst() <= 0)
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        var response =
                directed(
                                AuthDataAuthorityServiceGrpc.getReadIdentityAuthorityMethod()
                                        .getFullMethodName(),
                                deadlineNanos,
                                false)
                        .readIdentityAuthority(
                                ReadIdentityAuthorityRequest.newBuilder()
                                        .addAllUserIds(ids)
                                        .build());
        var result = new LinkedHashMap<Long, IdentityAuthoritySnapshot>();
        for (var identity : response.getIdentitiesList()) {
            if (!ids.contains(identity.getUserId())
                    || result.put(identity.getUserId(), identity) != null
                    || identity.getRolesVersion() <= 0
                    || identity.getTelegramBindingVersion() <= 0) throw unavailable();
        }
        if (result.size() != ids.size()) throw unavailable();
        return Map.copyOf(result);
    }

    public CheckAdminAccessResponse checkRegistryActor(long deadlineNanos) {
        return checkAdminAccess(java.util.List.of("USERS_VIEW"), false, deadlineNanos);
    }

    public CheckAdminAccessResponse checkAdminAccess(
            Collection<String> permissions, boolean requireFull, long deadlineNanos) {
        return directed(
                        AuthDataAuthorityServiceGrpc.getCheckAdminAccessMethod()
                                .getFullMethodName(),
                        deadlineNanos)
                .checkAdminAccess(
                        CheckAdminAccessRequest.newBuilder()
                                .addAllPermissions(
                                        permissions.stream().distinct().sorted().toList())
                                .setFullOnly(requireFull)
                                .build());
    }

    /** Forwards the original locally verified gRPC actor token; it never creates an actor JWT. */
    public CheckAdminAccessResponse checkAdminAccessForSignedGrpcActor(
            String signedActor,
            Collection<String> permissions,
            boolean requireFull,
            long deadlineNanos) {
        if (signedActor == null || signedActor.isBlank())
            throw Status.UNAUTHENTICATED.asRuntimeException();
        return directedWithActor(
                        AuthDataAuthorityServiceGrpc.getCheckAdminAccessMethod()
                                .getFullMethodName(),
                        deadlineNanos,
                        signedActor)
                .checkAdminAccess(
                        CheckAdminAccessRequest.newBuilder()
                                .addAllPermissions(
                                        permissions.stream().distinct().sorted().toList())
                                .setFullOnly(requireFull)
                                .build());
    }

    public ReadInitialCredentialsResponse initialCredentials(
            Collection<Long> selected, long deadlineNanos) {
        var ids = selected.stream().distinct().sorted().toList();
        if (ids.isEmpty() || ids.size() > 200 || ids.getFirst() <= 0)
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        return directed(
                        AuthDataAuthorityServiceGrpc.getReadInitialCredentialsMethod()
                                .getFullMethodName(),
                        deadlineNanos)
                .readInitialCredentials(
                        ReadInitialCredentialsRequest.newBuilder().addAllUserIds(ids).build());
    }

    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub directed(
            String method, long deadlineNanos) {
        return directed(method, deadlineNanos, true);
    }

    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub directed(
            String method, long deadlineNanos, boolean actorRequired) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || stub == null
                || authority == null
                || authority.isBlank()) throw unavailable();
        String actor = actorRequired ? context.getInternalToken() : null;
        if (actorRequired && (actor == null || actor.isBlank()))
            throw Status.UNAUTHENTICATED.asRuntimeException();
        return directedWithActor(method, deadlineNanos, actor);
    }

    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub directedWithActor(
            String method, long deadlineNanos, String actor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || stub == null
                || authority == null
                || authority.isBlank()) throw unavailable();
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthIdentityDataClient::unavailable);
        var metadata = new Metadata();
        if (actor != null)
            metadata.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), actor);
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                .withDeadlineAfter(
                        Math.min(remaining, TimeUnit.SECONDS.toNanos(3)), TimeUnit.NANOSECONDS);
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("current identity data unavailable")
                .asRuntimeException();
    }
}
