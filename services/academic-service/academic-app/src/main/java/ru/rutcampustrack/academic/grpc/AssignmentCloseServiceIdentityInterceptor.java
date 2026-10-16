package ru.rutcampustrack.academic.grpc;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

/**
 * Academic's directed receiver policy. Schedule owns prepared assignment-close lookups; Schedule
 * and Attendance may independently read authoritative semester state.
 */
public final class AssignmentCloseServiceIdentityInterceptor implements ServerInterceptor {

    static final String PROTECTED_METHOD =
            ServiceIdentityServerInterceptor.ACADEMIC_GET_PREPARED_ASSIGNMENT_CLOSE_OPERATION;
    static final String SEMESTER_STATE_METHOD =
            ServiceIdentityServerInterceptor.ACADEMIC_GET_SEMESTER_STATE;

    private static final ServicePrincipal ATTENDANCE_SERVICE =
            new ServicePrincipal("attendance-service");

    private final ServiceIdentityServerInterceptor delegate;
    private final ServiceIdentityServerInterceptor attendanceDelegate;
    private final byte[] attendanceTokenBytes;

    public AssignmentCloseServiceIdentityInterceptor(String scheduleToAcademicToken) {
        this(scheduleToAcademicToken, "");
    }

    public AssignmentCloseServiceIdentityInterceptor(
            String scheduleToAcademicToken, String attendanceToAcademicToken) {
        attendanceTokenBytes =
                DirectedServiceCredential.isCanonicalToken(attendanceToAcademicToken)
                        ? Base64.getUrlDecoder().decode(attendanceToAcademicToken)
                        : null;
        if (attendanceTokenBytes != null
                && DirectedServiceCredential.isCanonicalToken(scheduleToAcademicToken)
                && MessageDigest.isEqual(
                        attendanceTokenBytes,
                        Base64.getUrlDecoder().decode(scheduleToAcademicToken))) {
            throw new IllegalArgumentException("Directed service credentials must be distinct");
        }
        List<DirectedServiceCredential> credentials =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.SCHEDULE_SERVICE,
                                ServicePrincipal.ACADEMIC_SERVICE,
                                scheduleToAcademicToken)
                        .map(List::of)
                        .orElseGet(List::of);
        this.delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.ACADEMIC_SERVICE,
                        Map.of(
                                PROTECTED_METHOD, ServicePrincipal.SCHEDULE_SERVICE,
                                SEMESTER_STATE_METHOD, ServicePrincipal.SCHEDULE_SERVICE),
                        credentials);
        attendanceDelegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.ACADEMIC_SERVICE,
                        Map.of(SEMESTER_STATE_METHOD, ATTENDANCE_SERVICE),
                        DirectedServiceCredential.tryCreate(
                                        ATTENDANCE_SERVICE,
                                        ServicePrincipal.ACADEMIC_SERVICE,
                                        attendanceToAcademicToken)
                                .map(List::of)
                                .orElseGet(List::of));
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (SEMESTER_STATE_METHOD.equals(call.getMethodDescriptor().getFullMethodName())
                && matchesAttendanceCredential(call, headers)) {
            return attendanceDelegate.interceptCall(call, headers, next);
        }
        return delegate.interceptCall(call, headers, next);
    }

    private boolean matchesAttendanceCredential(ServerCall<?, ?> call, Metadata headers) {
        // Select only a locally configured identity, and never inspect credentials before TLS.
        if (attendanceTokenBytes == null
                || call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION) == null
                || headers == null) return false;
        Iterable<String> values = headers.getAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
        if (values == null) return false;
        var iterator = values.iterator();
        if (!iterator.hasNext()) return false;
        String candidate = iterator.next();
        return !iterator.hasNext()
                && DirectedServiceCredential.isCanonicalToken(candidate)
                && MessageDigest.isEqual(
                        attendanceTokenBytes, Base64.getUrlDecoder().decode(candidate));
    }
}
