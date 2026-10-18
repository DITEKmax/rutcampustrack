package ru.rutcampustrack.academic.identityoperation;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authoperations.grpc.AuthIdentityOperationServiceGrpc;
import ru.rutcampustrack.authoperations.grpc.CommitIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.FailIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.GetIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationResult;
import ru.rutcampustrack.authoperations.grpc.PrepareIdentityOperationRequest;
import ru.rutcampustrack.authoperations.grpc.RejectUnpreparedIdentityOperationRequest;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.util.concurrent.TimeUnit;

/** Fixed operation protocol; only initial Prepare carries a transient live actor token. */
@Component
public class AuthIdentityOperationClient implements AcademicIdentityCoordinator.Auth {
    @GrpcClient("auth-service")
    private AuthIdentityOperationServiceGrpc.AuthIdentityOperationServiceBlockingStub stub;

    private final RequestContext context;
    private final String token;
    private final String authority;

    public AuthIdentityOperationClient(
            RequestContext context,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.context = context;
        this.token = token;
        this.authority = authority;
    }

    @Override
    public IdentityOperationResult prepare(PrepareIdentityOperationRequest request) {
        String actor = context.getInternalToken();
        if (actor == null || actor.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
        var headers = new Metadata();
        headers.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), actor);
        return directed(
                        AuthIdentityOperationServiceGrpc.getPrepareIdentityOperationMethod()
                                .getFullMethodName())
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                .prepareIdentityOperation(request);
    }

    @Override
    public IdentityOperationResult get(GetIdentityOperationRequest request) {
        return directed(
                        AuthIdentityOperationServiceGrpc.getGetIdentityOperationMethod()
                                .getFullMethodName())
                .getIdentityOperation(request);
    }

    @Override
    public IdentityOperationResult commit(CommitIdentityOperationRequest request) {
        return directed(
                        AuthIdentityOperationServiceGrpc.getCommitIdentityOperationMethod()
                                .getFullMethodName())
                .commitIdentityOperation(request);
    }

    @Override
    public IdentityOperationResult fail(FailIdentityOperationRequest request) {
        return directed(
                        AuthIdentityOperationServiceGrpc.getFailIdentityOperationMethod()
                                .getFullMethodName())
                .failIdentityOperation(request);
    }

    @Override
    public IdentityOperationResult reject(RejectUnpreparedIdentityOperationRequest request) {
        return directed(
                        AuthIdentityOperationServiceGrpc
                                .getRejectUnpreparedIdentityOperationMethod()
                                .getFullMethodName())
                .rejectUnpreparedIdentityOperation(request);
    }

    private AuthIdentityOperationServiceGrpc.AuthIdentityOperationServiceBlockingStub directed(
            String method) {
        if (stub == null
                || TransactionSynchronizationManager.isActualTransactionActive()
                || authority == null
                || authority.isBlank()) throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthIdentityOperationClient::unavailable);
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withDeadlineAfter(3, TimeUnit.SECONDS);
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("identity operation authority unavailable")
                .asRuntimeException();
    }
}
