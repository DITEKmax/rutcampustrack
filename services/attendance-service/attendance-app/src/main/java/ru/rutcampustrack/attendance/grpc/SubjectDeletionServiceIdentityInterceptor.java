package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;

import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

/** The new mutation method requires the existing directed Academic credential over TLS. */
@GrpcGlobalServerInterceptor
public class SubjectDeletionServiceIdentityInterceptor implements ServerInterceptor {
    private static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    private final ServiceIdentityServerInterceptor delegate;

    public SubjectDeletionServiceIdentityInterceptor(
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String token) {
        var credentials =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE, ATTENDANCE, token)
                        .map(List::of)
                        .orElseGet(List::of);
        delegate =
                new ServiceIdentityServerInterceptor(
                        ATTENDANCE,
                        Map.of(
                                "rutcampustrack.subjectdeletion.SubjectDeletionParticipant/Apply",
                                ServicePrincipal.ACADEMIC_SERVICE),
                        credentials);
    }

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
