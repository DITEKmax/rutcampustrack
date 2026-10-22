package ru.rutcampustrack.academic.identitycatalog;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.authauthority.grpc.AuthDataAuthorityServiceGrpc;
import ru.rutcampustrack.authauthority.grpc.IdentityCatalogChangesRequest;
import ru.rutcampustrack.authauthority.grpc.IdentityCatalogChangesResponse;
import ru.rutcampustrack.authauthority.grpc.IdentityCatalogWatermarkRequest;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.util.concurrent.TimeUnit;

/** Specific protected registry feed, containing no session, permission profile or credentials. */
@Component
public class AuthIdentityCatalogClient {
    @GrpcClient("auth-service")
    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub stub;

    private final String token, authority;

    public AuthIdentityCatalogClient(
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.token = token;
        this.authority = authority;
    }

    public long watermark(long budgetNanos) {
        long value =
                directed(
                                AuthDataAuthorityServiceGrpc.getGetIdentityCatalogWatermarkMethod()
                                        .getFullMethodName(),
                                budgetNanos)
                        .getIdentityCatalogWatermark(
                                IdentityCatalogWatermarkRequest.getDefaultInstance())
                        .getCatalogVersion();
        if (value < 0) throw unavailable();
        return value;
    }

    public IdentityCatalogChangesResponse changes(long after, long budgetNanos) {
        if (after < 0) throw unavailable();
        return directed(
                        AuthDataAuthorityServiceGrpc.getReadIdentityCatalogChangesMethod()
                                .getFullMethodName(),
                        budgetNanos)
                .readIdentityCatalogChanges(
                        IdentityCatalogChangesRequest.newBuilder()
                                .setAfterCatalogVersion(after)
                                .setLimit(200)
                                .build());
    }

    private AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceBlockingStub directed(
            String method, long budgetNanos) {
        if (budgetNanos <= 0
                || stub == null
                || authority == null
                || authority.isBlank()
                || TransactionSynchronizationManager.isActualTransactionActive())
            throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthIdentityCatalogClient::unavailable);
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withDeadlineAfter(
                        Math.min(budgetNanos, TimeUnit.SECONDS.toNanos(3)), TimeUnit.NANOSECONDS);
    }

    static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("identity registry snapshot unavailable")
                .asRuntimeException();
    }
}
