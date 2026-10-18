package ru.rutcampustrack.auth.authority;

import io.grpc.Status;

import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.authauthority.grpc.AcademicIdentityPresentationServiceGrpc;
import ru.rutcampustrack.authauthority.grpc.IdentityPresentationRequest;
import ru.rutcampustrack.authauthority.grpc.IdentityPresentationResponse;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.util.concurrent.TimeUnit;

/** Pure presentation only: no live admission callback and no call under an Auth transaction. */
@Component
public final class AcademicIdentityPresentationClient {
    private static final String METHOD =
            "rutcampustrack.authauthority.AcademicIdentityPresentationService/ResolveIdentityPresentation";

    @GrpcClient("academic-service")
    private AcademicIdentityPresentationServiceGrpc.AcademicIdentityPresentationServiceBlockingStub
            stub;

    private final String token;
    private final String authority;

    public AcademicIdentityPresentationClient(
            @Value("${grpc.service-identity.auth-to-academic-presentation-token:}") String token,
            @Value("${grpc.client.academic-service.security.authority-override:academic-service}")
                    String authority) {
        this.token = token;
        this.authority = authority;
    }

    public IdentityPresentationResponse resolve(IdentityPresentationRequest request) {
        return directed(METHOD).resolveIdentityPresentation(request);
    }

    public ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibility headmanEligibility(
            ru.rutcampustrack.authauthority.grpc.HeadmanCredentialEligibilityRequest request) {
        return directed(
                        "rutcampustrack.authauthority.AcademicIdentityPresentationService/ResolveHeadmanCredentialEligibility")
                .resolveHeadmanCredentialEligibility(request);
    }

    private AcademicIdentityPresentationServiceGrpc.AcademicIdentityPresentationServiceBlockingStub
            directed(String method) {
        if (stub == null
                || org.springframework.transaction.support.TransactionSynchronizationManager
                        .isActualTransactionActive())
            throw Status.UNAVAILABLE
                    .withDescription("Academic pure read unavailable")
                    .asRuntimeException();
        var credential =
                DirectedServiceCredential.tryCreate(
                                AuthDirectedIdentityInterceptor.AUTH,
                                AuthDirectedIdentityInterceptor.ACADEMIC,
                                token)
                        .orElseThrow(
                                () ->
                                        Status.UNAVAILABLE
                                                .withDescription(
                                                        "identity presentation unavailable")
                                                .asRuntimeException());
        return stub.withCallCredentials(
                        ServiceTokenCallCredentials.forMethod(credential, authority, method))
                .withDeadlineAfter(3, TimeUnit.SECONDS);
    }
}
