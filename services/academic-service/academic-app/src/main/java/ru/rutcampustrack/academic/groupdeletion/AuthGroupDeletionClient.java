package ru.rutcampustrack.academic.groupdeletion;

import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Exact archive step-up. Password is forwarded once, never logged or persisted in Academic. */
@Component
public class AuthGroupDeletionClient {
    @GrpcClient("auth-service")
    private AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub stub;

    private final RequestContext context;
    private final String token;
    private final String authority;

    public AuthGroupDeletionClient(
            RequestContext context,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.context = context;
        this.token = token;
        this.authority = authority;
    }

    public PasswordStepUpReceipt confirm(
            long target, UUID operation, String digest, String password) {
        if (TransactionSynchronizationManager.isActualTransactionActive() || stub == null)
            throw unavailable();
        var actor = context.getClaims();
        String bearer = context.getInternalToken();
        if (actor == null || bearer == null || bearer.isBlank())
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Текущая сессия недействительна");
        if (password == null || password.isBlank() || password.length() > 256)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "password_required");
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthGroupDeletionClient::unavailable);
        var metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), bearer);
        var binding =
                PasswordStepUpBinding.newBuilder()
                        .setPurpose(PasswordStepUpPurpose.PASSWORD_STEP_UP_PURPOSE_GROUP_DELETE)
                        .setTargetId(target)
                        .setOperationId(operation.toString())
                        .setPreviewDigest(digest)
                        .build();
        try {
            var result =
                    stub.withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            credential,
                                            authority,
                                            AuthSecurityCapabilityServiceGrpc
                                                    .getConfirmPasswordStepUpMethod()
                                                    .getFullMethodName()))
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                            .confirmPasswordStepUp(
                                    PasswordStepUpRequest.newBuilder()
                                            .setBinding(binding)
                                            .setPassword(password)
                                            .build());
            var current = result.getActor();
            if (!result.getUnknownFields().asMap().isEmpty()
                    || !current.getUnknownFields().asMap().isEmpty()
                    || !current.getRole().equals("ADMIN")
                    || !current.getStatus().equals("ACTIVE")
                    || current.getReadOnly()
                    || current.getIsHeadman()
                    || current.hasGroupId()
                    || !result.getBinding().equals(binding)
                    || current.getUserId() != actor.userId()
                    || !current.getSessionId().equals(actor.sessionId().toString())
                    || current.getSessionVersion() != actor.sessionVersion()
                    || current.getRolesVersion() != actor.rolesVersion()
                    || result.getCredentialsVersion() <= 0
                    || result.getTelegramBindingVersion() <= 0
                    || !Instant.parse(result.getExpiresAt()).isAfter(Instant.now()))
                throw unavailable();
            if (!UUID.fromString(result.getReceiptId()).toString().equals(result.getReceiptId()))
                throw unavailable();
            return result;
        } catch (io.grpc.StatusRuntimeException denied) {
            HttpStatus status =
                    switch (denied.getStatus().getCode()) {
                        case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
                        case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
                        case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
                        case FAILED_PRECONDITION, NOT_FOUND -> HttpStatus.CONFLICT;
                        default -> HttpStatus.SERVICE_UNAVAILABLE;
                    };
            throw new ResponseStatusException(status, "Подтверждение удаления группы отклонено");
        } catch (java.time.DateTimeException | IllegalArgumentException malformed) {
            throw unavailable();
        }
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Auth не подтвердил удаление группы");
    }
}
