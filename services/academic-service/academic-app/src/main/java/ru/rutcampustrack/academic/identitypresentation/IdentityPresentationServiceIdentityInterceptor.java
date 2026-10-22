package ru.rutcampustrack.academic.identitypresentation;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.List;
import java.util.Map;

@GrpcGlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class IdentityPresentationServiceIdentityInterceptor implements ServerInterceptor {
    static final ServicePrincipal AUTH = new ServicePrincipal("auth-service");
    private static final String METHOD =
            "rutcampustrack.authauthority.AcademicIdentityPresentationService/ResolveIdentityPresentation";
    private static final String ELIGIBILITY_METHOD =
            "rutcampustrack.authauthority.AcademicIdentityPresentationService/ResolveHeadmanCredentialEligibility";
    private final ServiceIdentityServerInterceptor delegate;

    public IdentityPresentationServiceIdentityInterceptor(
            @Value("${grpc.service-identity.auth-to-academic-presentation-token:}") String token) {
        var credentials =
                DirectedServiceCredential.tryCreate(AUTH, ServicePrincipal.ACADEMIC_SERVICE, token)
                        .map(List::of)
                        .orElseGet(List::of);
        delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.ACADEMIC_SERVICE,
                        Map.of(METHOD, AUTH, ELIGIBILITY_METHOD, AUTH),
                        credentials);
    }

    public static boolean admitted(String method) {
        return (METHOD.equals(method) || ELIGIBILITY_METHOD.equals(method))
                && AUTH.equals(ServicePrincipal.CONTEXT_KEY.get());
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
