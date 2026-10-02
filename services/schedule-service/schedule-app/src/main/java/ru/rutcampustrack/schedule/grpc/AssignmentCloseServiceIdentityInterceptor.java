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
    static final String COMMIT_METHOD =
            ServiceIdentityServerInterceptor.SCHEDULE_COMMIT_ASSIGNMENT_CLOSE;
    static final String RESERVE_HOMEWORK_METHOD =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ReserveHomeworkBinding";
    static final String CONFIRM_HOMEWORK_METHOD =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ConfirmHomeworkBinding";
    static final String ARCHIVE_HOMEWORK_METHOD =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ArchiveHomeworkBinding";
    static final String GET_HOMEWORK_METHOD =
            ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetHomeworkBindings";
    static final String SEMESTER_ARCHIVE_BARRIER_METHOD =
            ServiceIdentityServerInterceptor.SCHEDULE_SET_SEMESTER_ARCHIVE_BARRIER;

    private final ServiceIdentityServerInterceptor delegate;

    public AssignmentCloseServiceIdentityInterceptor(String academicToScheduleToken) {
        List<DirectedServiceCredential> credentials = DirectedServiceCredential
                .tryCreate(ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE,
                        academicToScheduleToken)
                .map(List::of)
                .orElseGet(List::of);
        this.delegate = new ServiceIdentityServerInterceptor(
                ServicePrincipal.SCHEDULE_SERVICE,
                Map.ofEntries(
                        Map.entry(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(COMMIT_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(RESERVE_HOMEWORK_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(CONFIRM_HOMEWORK_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ARCHIVE_HOMEWORK_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(GET_HOMEWORK_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(SEMESTER_ARCHIVE_BARRIER_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetHomeworkBinding", ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/MoveHomeworkBinding", ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/ContinueHomeworkEdit", ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/AbortUnacceptedHomeworkEdit", ServicePrincipal.ACADEMIC_SERVICE),
                        Map.entry(ScheduleGrpcServiceGrpc.SERVICE_NAME + "/AcknowledgeHomeworkEdit", ServicePrincipal.ACADEMIC_SERVICE)),
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
