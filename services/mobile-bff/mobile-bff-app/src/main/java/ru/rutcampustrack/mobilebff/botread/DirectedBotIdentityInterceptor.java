package ru.rutcampustrack.mobilebff.botread;

import io.grpc.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.botreads.grpc.DirectedBotReadServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

/** The private bot receiver has no user-token or shared-secret admission fallback. */
@Component
public final class DirectedBotIdentityInterceptor implements ServerInterceptor {
    static final ServicePrincipal BOT = new ServicePrincipal("notification-bot");
    private static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");
    private final ServiceIdentityServerInterceptor delegate;

    public DirectedBotIdentityInterceptor(
            @Value("${grpc.service-identity.bot-to-mobile-bff-token:}") String token) {
        delegate =
                new ServiceIdentityServerInterceptor(
                        BFF,
                        Map.of(
                                DirectedBotReadServiceGrpc.getGetOwnTeacherTodayMethod()
                                                .getFullMethodName(),
                                        BOT,
                                DirectedBotReadServiceGrpc.getGetOwnHeadmanCurrentAttendanceMethod()
                                                .getFullMethodName(),
                                        BOT),
                        DirectedServiceCredential.tryCreate(BOT, BFF, token)
                                .map(List::of)
                                .orElseGet(List::of));
    }

    static void requireBot() {
        if (!BOT.equals(ServicePrincipal.CONTEXT_KEY.get())) {
            throw Status.UNAUTHENTICATED
                    .withDescription("verified bot identity required")
                    .asRuntimeException();
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
