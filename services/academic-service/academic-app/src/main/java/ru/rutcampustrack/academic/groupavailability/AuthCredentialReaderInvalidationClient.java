package ru.rutcampustrack.academic.groupavailability;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.concurrent.TimeUnit;

/** Exact group credential-reader protocol. Recovery carries no retained user bearer. */
@Component
public class AuthCredentialReaderInvalidationClient {
    @GrpcClient("auth-service")
    private AuthCredentialReaderInvalidationServiceGrpc
                    .AuthCredentialReaderInvalidationServiceBlockingStub
            stub;

    private final RequestContext context;
    private final String token;
    private final String authority;

    public AuthCredentialReaderInvalidationClient(
            RequestContext context,
            @Value("${grpc.service-identity.academic-to-auth-token:}") String token,
            @Value("${grpc.client.auth-service.security.authority-override:auth-service}")
                    String authority) {
        this.context = context;
        this.token = token;
        this.authority = authority;
    }

    public CredentialReaderInvalidationResult prepare(
            PrepareCredentialReaderInvalidationRequest request) {
        var call =
                directed(
                        AuthCredentialReaderInvalidationServiceGrpc
                                .getPrepareCredentialReaderInvalidationMethod()
                                .getFullMethodName());
        if (request.getAction()
                        != CredentialReaderInvalidationAction
                                .CREDENTIAL_READER_INVALIDATION_ACTION_AUTOMATIC_CYCLE_ARCHIVE
                || !request.getSupersedesFailedOperationId().isBlank()) {
            String actor = context.getInternalToken();
            if (actor == null || actor.isBlank()) throw Status.UNAUTHENTICATED.asRuntimeException();
            var headers = new Metadata();
            headers.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), actor);
            call = call.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
        }
        return call.prepareCredentialReaderInvalidation(request);
    }

    public CredentialReaderInvalidationResult get(GetCredentialReaderInvalidationRequest request) {
        return directed(
                        AuthCredentialReaderInvalidationServiceGrpc
                                .getGetCredentialReaderInvalidationMethod()
                                .getFullMethodName())
                .getCredentialReaderInvalidation(request);
    }

    public CredentialReaderInvalidationResult commit(
            CommitCredentialReaderInvalidationRequest request) {
        return directed(
                        AuthCredentialReaderInvalidationServiceGrpc
                                .getCommitCredentialReaderInvalidationMethod()
                                .getFullMethodName())
                .commitCredentialReaderInvalidation(request);
    }

    public CredentialReaderInvalidationResult fail(
            FailCredentialReaderInvalidationRequest request) {
        return directed(
                        AuthCredentialReaderInvalidationServiceGrpc
                                .getFailCredentialReaderInvalidationMethod()
                                .getFullMethodName())
                .failCredentialReaderInvalidation(request);
    }

    public CredentialReaderInvalidationResult reject(
            RejectUnpreparedCredentialReaderInvalidationRequest request) {
        return directed(
                        AuthCredentialReaderInvalidationServiceGrpc
                                .getRejectUnpreparedCredentialReaderInvalidationMethod()
                                .getFullMethodName())
                .rejectUnpreparedCredentialReaderInvalidation(request);
    }

    private AuthCredentialReaderInvalidationServiceGrpc
                    .AuthCredentialReaderInvalidationServiceBlockingStub
            directed(String method) {
        if (stub == null || TransactionSynchronizationManager.isActualTransactionActive())
            throw unavailable();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token)
                        .orElseThrow(AuthCredentialReaderInvalidationClient::unavailable);
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withDeadlineAfter(3, TimeUnit.SECONDS);
    }

    static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("group credential-reader operation requires recovery")
                .asRuntimeException();
    }
}
