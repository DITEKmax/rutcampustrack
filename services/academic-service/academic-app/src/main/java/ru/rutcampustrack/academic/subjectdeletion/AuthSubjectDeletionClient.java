package ru.rutcampustrack.academic.subjectdeletion;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.*;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** Auth owns password/OTP and current authority; no bearer or secret is stored in Academic. */
@Component
public class AuthSubjectDeletionClient {
    @GrpcClient("auth-service")
    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub stub;

    private final RequestContext context;
    private final String token, authority;
    private final Clock clock;

    public AuthSubjectDeletionClient(
            RequestContext context,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority,
            Clock clock) {
        this.context = context;
        this.token = token;
        this.authority = authority;
        this.clock = clock;
    }

    public Challenge challenge(
            SubjectDeletionAcademicData.Target target, ChallengeRequest request) {
        var result =
                invoke(
                        AuthSecurityCapabilityServiceGrpc.getChallengeSubjectDeletionMethod()
                                .getFullMethodName(),
                        client ->
                                client.challengeSubjectDeletion(
                                        SubjectDeletionChallengeRequest.newBuilder()
                                                .setBinding(
                                                        binding(
                                                                target,
                                                                request.operationId(),
                                                                request.previewDigest()))
                                                .setPassword(request.password())
                                                .build()));
        try {
            Instant expiry = Instant.parse(result.getExpiresAt());
            if (!result.getUnknownFields().asMap().isEmpty()
                    || !result.getChallengeId().matches("[A-Za-z0-9_-]{32}")
                    || !expiry.isAfter(clock.instant())
                    || result.getAttemptsRemaining() < 1
                    || result.getAttemptsRemaining() > 3) throw unavailable();
            return new Challenge(result.getChallengeId(), expiry, result.getAttemptsRemaining());
        } catch (java.time.DateTimeException malformed) {
            throw unavailable();
        }
    }

    public UUID verify(SubjectDeletionAcademicData.Target target, ConfirmRequest request) {
        var result =
                invoke(
                        AuthSecurityCapabilityServiceGrpc.getVerifySubjectDeletionMethod()
                                .getFullMethodName(),
                        client ->
                                client.verifySubjectDeletion(
                                        SubjectDeletionVerifyRequest.newBuilder()
                                                .setBinding(
                                                        binding(
                                                                target,
                                                                request.operationId(),
                                                                request.previewDigest()))
                                                .setChallengeId(request.challengeId())
                                                .setCode(request.code())
                                                .build()));
        if (!result.getAccepted() || !result.getUnknownFields().asMap().isEmpty())
            throw SubjectDeletionAcademicData.conflict();
        return canonicalUuid(result.getReceiptId());
    }

    public Admission check(
            SubjectDeletionAcademicData.Target target,
            UUID operation,
            String digest,
            UUID receipt) {
        var binding = binding(target, operation, digest);
        var actor = context.getClaims();
        var result =
                invoke(
                        AuthSecurityCapabilityServiceGrpc.getCheckSubjectDeletionReceiptMethod()
                                .getFullMethodName(),
                        client ->
                                client.checkSubjectDeletionReceipt(
                                        SubjectDeletionReceiptRequest.newBuilder()
                                                .setBinding(binding)
                                                .setReceiptId(receipt.toString())
                                                .build()));
        return admission(result, binding, receipt, actor, actor.userId(), actor.sessionId());
    }

    public Admission checkStored(SubjectDeletionOperation operation) {
        var binding =
                SubjectDeletionBinding.newBuilder()
                        .setSubjectId(operation.subject())
                        .setSemesterId(operation.semester())
                        .setGroupId(operation.group())
                        .setOperationId(operation.id().toString())
                        .setPreviewDigest(operation.digest())
                        .build();
        var result =
                invoke(
                        AuthSecurityCapabilityServiceGrpc
                                .getCheckStoredSubjectDeletionReceiptMethod()
                                .getFullMethodName(),
                        false,
                        client ->
                                client.checkStoredSubjectDeletionReceipt(
                                        SubjectDeletionReceiptRequest.newBuilder()
                                                .setBinding(binding)
                                                .setReceiptId(operation.proof().toString())
                                                .build()));
        return admission(
                result, binding, operation.proof(), null, operation.actor(), operation.sid());
    }

    private Admission admission(
            ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt result,
            SubjectDeletionBinding binding,
            UUID receipt,
            InternalJwtClaims actor,
            long user,
            UUID sid) {
        try {
            var identity = result.getActor();
            var expiry = Instant.parse(result.getExpiresAt());
            if (!result.getUnknownFields().asMap().isEmpty()
                    || !identity.getUnknownFields().asMap().isEmpty()
                    || !result.getBinding().equals(binding)
                    || !canonicalUuid(result.getReceiptId()).equals(receipt)
                    || identity.getUserId() != user
                    || !identity.getSessionId().equals(sid.toString())
                    || identity.getSessionVersion() <= 0
                    || identity.getRolesVersion() <= 0
                    || !identity.getRole().equals("HEADMAN")
                    || !identity.getStatus().equals("ACTIVE")
                    || !identity.hasGroupId()
                    || identity.getGroupId() != binding.getGroupId()
                    || !identity.getIsHeadman()
                    || identity.getReadOnly()
                    || actor != null && !sameActor(identity, actor)
                    || result.getCredentialsVersion() <= 0
                    || result.getTelegramBindingVersion() <= 0
                    || result.getTelegramId() <= 0
                    || !expiry.isAfter(clock.instant())) throw unavailable();
            return new Admission(result, clock.instant());
        } catch (java.time.DateTimeException malformed) {
            throw unavailable();
        }
    }

    private <T> T invoke(
            String method,
            Function<AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub, T>
                    call) {
        return invoke(method, true, call);
    }

    private <T> T invoke(
            String method,
            boolean actorRequired,
            Function<AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub, T>
                    call) {
        if (stub == null
                || TransactionSynchronizationManager.isActualTransactionActive()
                || authority == null
                || authority.isBlank()) throw unavailable();
        String bearer = actorRequired ? context.getInternalToken() : null;
        if (actorRequired && (context.getClaims() == null || bearer == null || bearer.isBlank()))
            throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthSubjectDeletionClient::unavailable);
        var metadata = new Metadata();
        if (actorRequired)
            metadata.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), bearer);
        try {
            return call.apply(
                    stub.withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            credential, authority, method))
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                            .withDeadlineAfter(3, TimeUnit.SECONDS));
        } catch (io.grpc.StatusRuntimeException rejected) {
            HttpStatus status =
                    switch (rejected.getStatus().getCode()) {
                        case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                        case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                        case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                        case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
                        case FAILED_PRECONDITION, NOT_FOUND, ABORTED -> HttpStatus.CONFLICT;
                        default -> HttpStatus.SERVICE_UNAVAILABLE;
                    };
            throw new ResponseStatusException(status, "Подтверждение удаления не принято");
        }
    }

    private static boolean sameActor(StoredSessionIdentity value, InternalJwtClaims actor) {
        return actor != null
                && actor.sessionId() != null
                && value.getUserId() == actor.userId()
                && value.getSessionId().equals(actor.sessionId().toString())
                && value.getSessionVersion() == actor.sessionVersion()
                && value.getRolesVersion() == actor.rolesVersion()
                && value.getRole().equals(actor.role())
                && value.getRole().equals("HEADMAN")
                && value.getStatus().equals(actor.status())
                && value.getStatus().equals("ACTIVE")
                && value.hasGroupId()
                && java.util.Objects.equals(value.getGroupId(), actor.groupId())
                && value.getIsHeadman()
                && actor.isHeadman()
                && !value.getReadOnly()
                && !actor.readOnly();
    }

    private static SubjectDeletionBinding binding(
            SubjectDeletionAcademicData.Target target, UUID operation, String digest) {
        return SubjectDeletionBinding.newBuilder()
                .setSubjectId(target.subject())
                .setSemesterId(target.semester())
                .setGroupId(target.group())
                .setOperationId(operation.toString())
                .setPreviewDigest(digest)
                .build();
    }

    private static UUID canonicalUuid(String value) {
        try {
            var id = UUID.fromString(value);
            if (!id.toString().equals(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Подтверждение удаления недоступно");
    }

    /**
     * Only a successful exact Auth RPC can construct an admission. It is never persisted as a
     * bearer.
     */
    static final class Admission {
        private final ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt receipt;
        private final Instant checkedAt;

        private Admission(
                ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt receipt,
                Instant checkedAt) {
            this.receipt = receipt;
            this.checkedAt = checkedAt;
        }

        Instant checkedAt() {
            return checkedAt;
        }

        UUID proof() {
            return UUID.fromString(receipt.getReceiptId());
        }

        boolean matches(
                UUID proof,
                UUID operation,
                String digest,
                long subject,
                long semester,
                long group,
                long actor,
                UUID sid,
                Instant now) {
            var binding = receipt.getBinding();
            return proof().equals(proof)
                    && binding.getOperationId().equals(operation.toString())
                    && binding.getPreviewDigest().equals(digest)
                    && binding.getSubjectId() == subject
                    && binding.getSemesterId() == semester
                    && binding.getGroupId() == group
                    && receipt.getActor().getUserId() == actor
                    && receipt.getActor().getSessionId().equals(sid.toString())
                    && !now.isBefore(checkedAt)
                    && Instant.parse(receipt.getExpiresAt()).isAfter(now);
        }
    }
}
