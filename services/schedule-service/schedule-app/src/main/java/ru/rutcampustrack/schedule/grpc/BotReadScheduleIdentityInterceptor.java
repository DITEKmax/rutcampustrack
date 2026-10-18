package ru.rutcampustrack.schedule.grpc;

import io.grpc.*;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import ru.rutcampustrack.shared.security.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Exact directed read credentials; all existing Schedule consumers keep their admission. */
@GrpcGlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class BotReadScheduleIdentityInterceptor implements ServerInterceptor {
    static final ServicePrincipal BFF = new ServicePrincipal("mobile-bff");
    static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    static final ServicePrincipal ACADEMIC_REPORT = new ServicePrincipal("academic-service");
    static final String DAY = ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetLessonsByGroup";
    static final String LESSON = ScheduleGrpcServiceGrpc.SERVICE_NAME + "/GetLessonById";
    private final ServiceIdentityServerInterceptor delegate;
    private final List<String> tokens;
    private final ServiceIdentityServerInterceptor reportDelegate;
    private final String reportToken;

    @org.springframework.beans.factory.annotation.Autowired
    public BotReadScheduleIdentityInterceptor(
            @Value("${grpc.service-identity.mobile-bff-to-schedule-token:}") String bffToken,
            @Value("${grpc.service-identity.attendance-bot-read-to-schedule-token:}")
                    String attendanceToken,
            @Value("${grpc.service-identity.academic-report-to-schedule-token:}")
                    String academicReportToken) {
        List<String> candidateTokens =
                java.util.stream.Stream.of(bffToken, attendanceToken, academicReportToken)
                        .filter(DirectedServiceCredential::isCanonicalToken)
                        .toList();
        if (candidateTokens.stream().distinct().count() != candidateTokens.size()) {
            throw new IllegalArgumentException("Bot read service credentials must be distinct");
        }
        List<DirectedServiceCredential> credentials = new ArrayList<>();
        List<String> configured = new ArrayList<>();
        DirectedServiceCredential.tryCreate(BFF, ServicePrincipal.SCHEDULE_SERVICE, bffToken)
                .ifPresent(
                        value -> {
                            credentials.add(value);
                            configured.add(bffToken);
                        });
        DirectedServiceCredential.tryCreate(
                        ATTENDANCE, ServicePrincipal.SCHEDULE_SERVICE, attendanceToken)
                .ifPresent(
                        value -> {
                            credentials.add(value);
                            configured.add(attendanceToken);
                        });
        delegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.SCHEDULE_SERVICE,
                        Map.of(DAY, BFF, LESSON, ATTENDANCE),
                        credentials);
        var reportCredential =
                DirectedServiceCredential.tryCreate(
                        ACADEMIC_REPORT, ServicePrincipal.SCHEDULE_SERVICE, academicReportToken);
        reportDelegate =
                new ServiceIdentityServerInterceptor(
                        ServicePrincipal.SCHEDULE_SERVICE,
                        Map.of(DAY, ACADEMIC_REPORT),
                        reportCredential.stream().toList());
        reportToken = reportCredential.isPresent() ? academicReportToken : null;
        if (reportToken != null) configured.add(reportToken);
        tokens = List.copyOf(configured);
    }

    public BotReadScheduleIdentityInterceptor(String bffToken, String attendanceToken) {
        this(bffToken, attendanceToken, "");
    }

    static boolean admitted(ServerCall<?, ?> call) {
        String method = call.getMethodDescriptor().getFullMethodName();
        ServicePrincipal caller = ServicePrincipal.CONTEXT_KEY.get();
        return DAY.equals(method) && (BFF.equals(caller) || ACADEMIC_REPORT.equals(caller))
                || LESSON.equals(method) && ATTENDANCE.equals(caller);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String method = call.getMethodDescriptor().getFullMethodName();
        Iterable<String> supplied = headers.getAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
        if (supplied == null) return next.startCall(call, headers);
        if (DAY.equals(method) || LESSON.equals(method)) {
            if (DAY.equals(method) && reportToken != null) {
                for (String candidate : supplied) {
                    if (equalToken(reportToken, candidate))
                        return reportDelegate.interceptCall(call, headers, next);
                }
            }
            return delegate.interceptCall(call, headers, next);
        }
        // These tokens cannot inherit the permissive/unconfigured legacy-secret path on any RPC.
        for (String candidate : supplied) {
            if (candidate != null
                    && tokens.stream().anyMatch(token -> equalToken(token, candidate))) {
                call.close(
                        Status.PERMISSION_DENIED.withDescription(
                                "Bot producer read is outside its scope"),
                        new Metadata());
                return new ServerCall.Listener<>() {};
            }
        }
        return next.startCall(call, headers);
    }

    private static boolean equalToken(String expected, String candidate) {
        return candidate != null
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.US_ASCII),
                        candidate.getBytes(StandardCharsets.US_ASCII));
    }
}
