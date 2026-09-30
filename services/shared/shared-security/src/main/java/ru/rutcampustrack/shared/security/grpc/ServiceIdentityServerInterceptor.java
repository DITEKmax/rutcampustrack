package ru.rutcampustrack.shared.security.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

import javax.net.ssl.SSLSession;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Reusable exact-method service identity admission.
 *
 * <p>The interceptor is intentionally inert for methods absent from the
 * supplied policy. This lets an application register it globally while the
 * existing contracts of unrelated RPCs remain owned by their existing
 * interceptors.</p>
 */
public final class ServiceIdentityServerInterceptor implements ServerInterceptor {

    public static final String SCHEDULE_INSTALL_ASSIGNMENT_CLOSE_CAP =
            "rutcampustrack.schedule.ScheduleGrpcService/InstallAssignmentCloseCap";
    public static final String SCHEDULE_COMMIT_ASSIGNMENT_CLOSE =
            "rutcampustrack.schedule.ScheduleGrpcService/CommitAssignmentClose";
    public static final String ACADEMIC_GET_PREPARED_ASSIGNMENT_CLOSE_OPERATION =
            "rutcampustrack.academic.AcademicGrpcService/GetPreparedAssignmentCloseOperation";
    public static final String ACADEMIC_GET_SEMESTER_STATE =
            "rutcampustrack.academic.AcademicGrpcService/GetSemesterState";
    public static final String SCHEDULE_SET_SEMESTER_ARCHIVE_BARRIER =
            "rutcampustrack.schedule.ScheduleGrpcService/SetSemesterArchiveBarrier";

    private static final String INVALID_IDENTITY = "Invalid service identity";
    private static final String TLS_REQUIRED = "Service identity requires authenticated TLS";
    private static final String FORBIDDEN_CALLER = "Service identity is not allowed for this method";

    private final ServicePrincipal target;
    private final Map<String, ServicePrincipal> allowedCallersByMethod;
    private final List<DirectedServiceCredential> credentials;

    public ServiceIdentityServerInterceptor(
            ServicePrincipal target,
            Map<String, ServicePrincipal> allowedCallersByMethod,
            Collection<DirectedServiceCredential> credentials) {
        if (target == null || allowedCallersByMethod == null || credentials == null) {
            throw new IllegalArgumentException("Service identity policy is required");
        }
        this.target = target;
        this.allowedCallersByMethod = Map.copyOf(allowedCallersByMethod);
        this.credentials = List.copyOf(credentials);
    }

    public ServiceIdentityServerInterceptor(
            ServicePrincipal target,
            Collection<DirectedServiceCredential> credentials,
            Map<String, ServicePrincipal> allowedCallersByMethod) {
        this(target, allowedCallersByMethod, credentials);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String methodName = call == null || call.getMethodDescriptor() == null
                ? ""
                : call.getMethodDescriptor().getFullMethodName();
        ServicePrincipal expectedCaller = allowedCallersByMethod.get(methodName);
        if (expectedCaller == null) {
            return next.startCall(call, headers);
        }

        if (!hasAuthenticatedTls(call)) {
            return reject(call, Status.Code.UNAUTHENTICATED, TLS_REQUIRED);
        }

        String token = exactlyOneToken(headers);
        if (token == null) {
            return reject(call, Status.Code.UNAUTHENTICATED, INVALID_IDENTITY);
        }

        DirectedServiceCredential verified = verifyToken(token);
        if (verified == null) {
            return reject(call, Status.Code.UNAUTHENTICATED, INVALID_IDENTITY);
        }
        if (!expectedCaller.equals(verified.source())) {
            return reject(call, Status.Code.PERMISSION_DENIED, FORBIDDEN_CALLER);
        }

        Context context = Context.current().withValue(ServicePrincipal.CONTEXT_KEY, verified.source());
        return Contexts.interceptCall(context, call, headers, next);
    }

    private boolean hasAuthenticatedTls(ServerCall<?, ?> call) {
        if (call == null || call.getAttributes() == null) {
            return false;
        }
        SSLSession session = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        return session != null;
    }

    private DirectedServiceCredential verifyToken(String token) {
        for (DirectedServiceCredential credential : credentials) {
            if (target.equals(credential.target()) && credential.matches(token)) {
                return credential;
            }
        }
        return null;
    }

    private static String exactlyOneToken(Metadata headers) {
        if (headers == null) {
            return null;
        }
        List<String> values = new ArrayList<>(2);
        Iterable<String> allValues = headers.getAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
        if (allValues == null) {
            return null;
        }
        for (String value : allValues) {
            if (values.size() == 2) {
                break;
            }
            values.add(value);
        }
        if (values.size() != 1) {
            return null;
        }
        String token = values.get(0);
        return DirectedServiceCredential.isCanonicalToken(token) ? token : null;
    }

    private static <ReqT> ServerCall.Listener<ReqT> reject(
            ServerCall<ReqT, ?> call,
            Status.Code code,
            String description) {
        call.close(Status.fromCode(code).withDescription(description), new Metadata());
        return new ServerCall.Listener<>() { };
    }
}
