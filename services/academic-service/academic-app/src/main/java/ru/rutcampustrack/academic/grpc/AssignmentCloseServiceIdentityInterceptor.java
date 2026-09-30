package ru.rutcampustrack.academic.grpc;

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
 * Academic's fixed receiver policy for the future assignment-close lookup.
 * The interceptor has no authority over the current close domain operation.
 */
public final class AssignmentCloseServiceIdentityInterceptor implements ServerInterceptor {

    static final String PROTECTED_METHOD =
            ServiceIdentityServerInterceptor.ACADEMIC_GET_PREPARED_ASSIGNMENT_CLOSE_OPERATION;
    static final String SEMESTER_STATE_METHOD =
            ServiceIdentityServerInterceptor.ACADEMIC_GET_SEMESTER_STATE;

    private final ServiceIdentityServerInterceptor delegate;

    public AssignmentCloseServiceIdentityInterceptor(String scheduleToAcademicToken) {
        List<DirectedServiceCredential> credentials = DirectedServiceCredential
                .tryCreate(ServicePrincipal.SCHEDULE_SERVICE, ServicePrincipal.ACADEMIC_SERVICE,
                        scheduleToAcademicToken)
                .map(List::of)
                .orElseGet(List::of);
        this.delegate = new ServiceIdentityServerInterceptor(
                ServicePrincipal.ACADEMIC_SERVICE,
                Map.of(
                        PROTECTED_METHOD, ServicePrincipal.SCHEDULE_SERVICE,
                        SEMESTER_STATE_METHOD, ServicePrincipal.SCHEDULE_SERVICE),
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
