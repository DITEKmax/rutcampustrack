package ru.rutcampustrack.attendance.botread;

import io.grpc.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.botreads.grpc.AttendanceOwnBotReadServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

@Component
public final class AttendanceBotIdentityInterceptor implements ServerInterceptor {
    private static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");
    private static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    private final ServiceIdentityServerInterceptor delegate;

    public AttendanceBotIdentityInterceptor(
            @Value("${grpc.service-identity.mobile-bff-to-attendance-token:}") String token) {
        delegate =
                new ServiceIdentityServerInterceptor(
                        ATTENDANCE,
                        Map.of(
                                AttendanceOwnBotReadServiceGrpc.getGetOwnBotLessonAttendanceMethod()
                                        .getFullMethodName(),
                                BFF),
                        DirectedServiceCredential.tryCreate(BFF, ATTENDANCE, token)
                                .map(List::of)
                                .orElseGet(List::of));
    }

    static void requireBff() {
        if (!BFF.equals(ServicePrincipal.CONTEXT_KEY.get())) {
            throw Status.UNAUTHENTICATED
                    .withDescription("verified MobileBFF identity required")
                    .asRuntimeException();
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
