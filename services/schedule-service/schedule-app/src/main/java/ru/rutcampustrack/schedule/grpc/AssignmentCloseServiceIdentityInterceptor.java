package ru.rutcampustrack.schedule.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.List;
import java.util.Map;

/**
 * Schedule's fixed receiver policy for the future assignment-close command.
 * This boundary authenticates the caller only; domain authorization is later.
 */
public final class AssignmentCloseServiceIdentityInterceptor implements ServerInterceptor {

    static final String PROTECTED_METHOD =
            ServiceIdentityServerInterceptor.SCHEDULE_INSTALL_ASSIGNMENT_CLOSE_CAP;

    private final ServiceIdentityServerInterceptor delegate;

    public AssignmentCloseServiceIdentityInterceptor(String academicToScheduleToken) {
        List<DirectedServiceCredential> credentials = DirectedServiceCredential
                .tryCreate(ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE,
                        academicToScheduleToken)
                .map(List::of)
                .orElseGet(List::of);
        this.delegate = new ServiceIdentityServerInterceptor(
                ServicePrincipal.SCHEDULE_SERVICE,
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                credentials);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        return delegate.interceptCall(call, headers, next);
    }
}
