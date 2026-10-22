package ru.rutcampustrack.academic.botread;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authauthority.grpc.AuthIdentityGrpcServiceGrpc;
import ru.rutcampustrack.authauthority.grpc.BotReadCapability;
import ru.rutcampustrack.authauthority.grpc.CheckBotOwnAuthorityRequest;
import ru.rutcampustrack.authauthority.grpc.CheckBotOwnAuthorityResponse;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.util.concurrent.TimeUnit;

/** Auth owns binding, admission grants, generations and operation fences. Never cached. */
@Component
public class AuthBotReadAuthorityClient {
    @GrpcClient("auth-service")
    private AuthIdentityGrpcServiceGrpc.AuthIdentityGrpcServiceBlockingStub stub;

    private final String token;
    private final String authority;

    public AuthBotReadAuthorityClient(
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.token = token;
        this.authority = authority;
    }

    public CheckBotOwnAuthorityResponse check(
            DirectedBotIdentity identity, BotReadCapability capability) {
        validate(identity);
        if (TransactionSynchronizationManager.isActualTransactionActive() || stub == null) {
            throw unavailable();
        }
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthBotReadAuthorityClient::unavailable);
        if (authority == null || authority.isBlank()) throw unavailable();
        var result =
                stub.withCallCredentials(
                                ServiceTokenCallCredentials.forMethod(
                                        credential,
                                        authority,
                                        AuthIdentityGrpcServiceGrpc
                                                .getCheckAcademicBotOwnAuthorityMethod()
                                                .getFullMethodName()))
                        .withDeadlineAfter(3, TimeUnit.SECONDS)
                        .checkAcademicBotOwnAuthority(
                                CheckBotOwnAuthorityRequest.newBuilder()
                                        .setTelegramSenderId(identity.getTelegramSenderId())
                                        .setPrivateChatId(identity.getPrivateChatId())
                                        .setExpectedUserId(identity.getExpectedUserId())
                                        .setExpectedTelegramBindingVersion(
                                                identity.getExpectedBindingVersion())
                                        .setExpectedRolesVersion(identity.getExpectedRolesVersion())
                                        .setRequiredCapability(capability)
                                        .build());
        if (result.getUserId() != identity.getExpectedUserId()
                || result.getTelegramBindingVersion() != identity.getExpectedBindingVersion()
                || result.getRolesVersion() != identity.getExpectedRolesVersion()
                || result.getCapability() != capability) throw unavailable();
        return result;
    }

    public static void validate(DirectedBotIdentity identity) {
        if (identity == null
                || identity.getTelegramSenderId() <= 0
                || identity.getPrivateChatId() != identity.getTelegramSenderId()
                || identity.getExpectedUserId() <= 0
                || identity.getExpectedBindingVersion() <= 0
                || identity.getExpectedRolesVersion() <= 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("bound private identity is required")
                    .asRuntimeException();
        }
    }

    static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("own-read authority unavailable")
                .asRuntimeException();
    }
}
