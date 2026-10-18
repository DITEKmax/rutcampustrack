package ru.rutcampustrack.auth.botcredentials;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.List;
import java.util.Map;

/** Admission uses a credential unavailable to Academic, scoped to this Auth TLS method. */
@Component
public final class AuthBotCredentialServiceIdentityInterceptor implements ServerInterceptor {
    public static final ServicePrincipal BOT = new ServicePrincipal("notification-bot");
    public static final ServicePrincipal AUTH = new ServicePrincipal("auth-service");
    public static final String READ_METHOD =
            "rutcampustrack.academic.AcademicGrpcService/GetOwnBotInitialCredentials";

    private final ServiceIdentityServerInterceptor delegate;

    public AuthBotCredentialServiceIdentityInterceptor(
            @Value("${grpc.service-identity.bot-to-auth-token:}") String token) {
        var credentials =
                DirectedServiceCredential.tryCreate(BOT, AUTH, token)
                        .map(List::of)
                        .orElseGet(List::of);
        delegate =
                new ServiceIdentityServerInterceptor(AUTH, Map.of(READ_METHOD, BOT), credentials);
    }

    public static void requireBot() {
        if (!BOT.equals(ServicePrincipal.CONTEXT_KEY.get())) {
            throw Status.UNAUTHENTICATED
                    .withDescription("verified Auth bot identity required")
                    .asRuntimeException();
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
