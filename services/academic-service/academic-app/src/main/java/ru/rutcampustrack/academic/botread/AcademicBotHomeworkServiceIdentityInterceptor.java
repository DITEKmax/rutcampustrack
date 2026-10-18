package ru.rutcampustrack.academic.botread;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.List;
import java.util.Map;

/** Fixed bot-to-Academic own-homework TLS policy; credentials are hosted by Auth. */
@GrpcGlobalServerInterceptor
public final class AcademicBotHomeworkServiceIdentityInterceptor implements ServerInterceptor {

    public static final ServicePrincipal BOT = new ServicePrincipal("notification-bot");
    public static final String HOMEWORK_METHOD =
            "rutcampustrack.academic.AcademicGrpcService/GetOwnBotHomeworksForWeek";

    private final ServiceIdentityServerInterceptor delegate;

    public AcademicBotHomeworkServiceIdentityInterceptor(
            @Value("${grpc.service-identity.bot-to-academic-token:}") String token) {
        var credentials =
                DirectedServiceCredential.tryCreate(BOT, ServicePrincipal.ACADEMIC_SERVICE, token)
                        .map(List::of)
                        .orElseGet(List::of);
        delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.ACADEMIC_SERVICE,
                        Map.of(HOMEWORK_METHOD, BOT),
                        credentials);
    }

    /** Domain code also fails closed if the receiver policy was not registered. */
    public static void requireBot() {
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
