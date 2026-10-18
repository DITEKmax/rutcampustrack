package ru.rutcampustrack.attendance.report.planned;

import io.grpc.*;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import ru.rutcampustrack.reportrendering.grpc.ReportRenderingServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

/** A report-only credential and independent purpose token cannot authorize ordinary CRUD. */
@GrpcGlobalServerInterceptor
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class PlannedReportIdentityInterceptor implements ServerInterceptor {
    static final Context.Key<String> CAPABILITY =
            Context.key("attendance-planned-report-capability");
    static final Metadata.Key<String> CAPABILITY_HEADER =
            Metadata.Key.of("x-report-capability", Metadata.ASCII_STRING_MARSHALLER);
    private final ServiceIdentityServerInterceptor delegate;
    private final String configuredToken;

    public PlannedReportIdentityInterceptor(
            @Value(
                            "${grpc.service-identity.academic-report-to-attendance-token:${ACADEMIC_REPORT_TO_ATTENDANCE_SERVICE_TOKEN:}}")
                    String token) {
        configuredToken = DirectedServiceCredential.isCanonicalToken(token) ? token : null;
        delegate =
                new ServiceIdentityServerInterceptor(
                        new ServicePrincipal("attendance-service"),
                        Map.of(
                                ReportRenderingServiceGrpc.getRenderReportMethod()
                                        .getFullMethodName(),
                                ServicePrincipal.ACADEMIC_SERVICE),
                        DirectedServiceCredential.tryCreate(
                                        ServicePrincipal.ACADEMIC_SERVICE,
                                        new ServicePrincipal("attendance-service"),
                                        token)
                                .map(List::of)
                                .orElseGet(List::of));
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!ReportRenderingServiceGrpc.getRenderReportMethod()
                .getFullMethodName()
                .equals(call.getMethodDescriptor().getFullMethodName())) {
            Iterable<String> tokens = headers.getAll(DirectedServiceCredential.TOKEN_METADATA_KEY);
            if (tokens != null && configuredToken != null) {
                for (String candidate : tokens) {
                    if (candidate != null
                            && java.security.MessageDigest.isEqual(
                                    configuredToken.getBytes(
                                            java.nio.charset.StandardCharsets.US_ASCII),
                                    candidate.getBytes(
                                            java.nio.charset.StandardCharsets.US_ASCII))) {
                        call.close(
                                Status.PERMISSION_DENIED.withDescription(
                                        "Report credential is outside its method scope"),
                                new Metadata());
                        return new ServerCall.Listener<>() {};
                    }
                }
            }
            return next.startCall(call, headers);
        }
        return delegate.interceptCall(
                call,
                headers,
                (verifiedCall, verifiedHeaders) -> {
                    String token = exactlyOneCapability(verifiedHeaders);
                    if (token == null) {
                        verifiedCall.close(
                                Status.UNAUTHENTICATED.withDescription(
                                        "Report capability required"),
                                new Metadata());
                        return new ServerCall.Listener<>() {};
                    }
                    return Contexts.interceptCall(
                            Context.current().withValue(CAPABILITY, token),
                            verifiedCall,
                            verifiedHeaders,
                            next);
                });
    }

    static String requireCapability() {
        String capability = CAPABILITY.get();
        if (!ServicePrincipal.ACADEMIC_SERVICE.equals(ServicePrincipal.CONTEXT_KEY.get())
                || capability == null) {
            throw Status.UNAUTHENTICATED
                    .withDescription("Verified Academic report authority required")
                    .asRuntimeException();
        }
        return capability;
    }

    private static String exactlyOneCapability(Metadata headers) {
        Iterable<String> values = headers.getAll(CAPABILITY_HEADER);
        if (values == null) return null;
        String result = null;
        for (String value : values) {
            if (result != null || value == null || value.isBlank() || value.length() > 16_384)
                return null;
            result = value;
        }
        return result;
    }
}
