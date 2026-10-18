package ru.rutcampustrack.academic.botread;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.ArrayList;
import java.util.Map;

/** Each own-read method admits exactly one directed service principal. */
@GrpcGlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AcademicBotReadServiceIdentityInterceptor implements ServerInterceptor {
    public static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");
    public static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    private static final String PREFIX = "rutcampustrack.botreads.AcademicOwnBotReadService/";
    private final ServiceIdentityServerInterceptor delegate;

    public AcademicBotReadServiceIdentityInterceptor(
            @Value("${grpc.service-identity.mobile-bff-to-academic-token:}") String bffToken,
            @Value("${grpc.service-identity.attendance-bot-read-to-academic-token:}")
                    String attendanceToken) {
        var credentials = new ArrayList<DirectedServiceCredential>();
        DirectedServiceCredential.tryCreate(BFF, ServicePrincipal.ACADEMIC_SERVICE, bffToken)
                .ifPresent(credentials::add);
        DirectedServiceCredential.tryCreate(
                        ATTENDANCE, ServicePrincipal.ACADEMIC_SERVICE, attendanceToken)
                .ifPresent(credentials::add);
        delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.ACADEMIC_SERVICE,
                        Map.of(
                                PREFIX + "ResolveOwnBotReadScope",
                                BFF,
                                PREFIX + "ResolveAttendanceOwnBotReadScope",
                                ATTENDANCE),
                        credentials);
    }

    static void require(ServicePrincipal caller) {
        if (!caller.equals(ServicePrincipal.CONTEXT_KEY.get())) {
            throw Status.UNAUTHENTICATED
                    .withDescription("verified service identity required")
                    .asRuntimeException();
        }
    }

    public static boolean admitted(String method) {
        var caller = ServicePrincipal.CONTEXT_KEY.get();
        return (PREFIX + "ResolveOwnBotReadScope").equals(method) && BFF.equals(caller)
                || (PREFIX + "ResolveAttendanceOwnBotReadScope").equals(method)
                        && ATTENDANCE.equals(caller);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
