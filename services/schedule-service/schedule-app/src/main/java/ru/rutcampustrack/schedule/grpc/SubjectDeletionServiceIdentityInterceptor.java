package ru.rutcampustrack.schedule.grpc;

import io.grpc.*;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

/** The new mutation method requires the existing directed Academic credential over TLS. */
@GrpcGlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SubjectDeletionServiceIdentityInterceptor implements ServerInterceptor {
    private static final String METHOD =
            "rutcampustrack.subjectdeletion.SubjectDeletionParticipant/Apply";
    private final ServiceIdentityServerInterceptor delegate;

    public SubjectDeletionServiceIdentityInterceptor(
            @Value("${grpc.service-identity.academic-to-schedule-token:}") String token) {
        var credentials =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                ServicePrincipal.SCHEDULE_SERVICE,
                                token)
                        .map(List::of)
                        .orElseGet(List::of);
        delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.SCHEDULE_SERVICE,
                        Map.of(
                                METHOD,
                                ServicePrincipal.ACADEMIC_SERVICE,
                                "rutcampustrack.groupdeletion.GroupDeletionParticipant/Apply",
                                ServicePrincipal.ACADEMIC_SERVICE),
                        credentials);
    }

    static boolean admitted(ServerCall<?, ?> call) {
        return (METHOD.equals(call.getMethodDescriptor().getFullMethodName())
                        || "rutcampustrack.groupdeletion.GroupDeletionParticipant/Apply"
                                .equals(call.getMethodDescriptor().getFullMethodName()))
                && ServicePrincipal.ACADEMIC_SERVICE.equals(ServicePrincipal.CONTEXT_KEY.get());
    }

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
