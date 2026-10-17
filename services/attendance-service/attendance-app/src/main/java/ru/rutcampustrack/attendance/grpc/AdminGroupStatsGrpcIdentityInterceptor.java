package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;

import org.springframework.beans.factory.annotation.Value;

import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.List;
import java.util.Map;

/**
 * Exact method registration: TLS/service caller admission plus the existing signed user identity.
 */
@GrpcGlobalServerInterceptor
public final class AdminGroupStatsGrpcIdentityInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final InternalJwtValidator validator;
    private final ServiceIdentityServerInterceptor service;

    public AdminGroupStatsGrpcIdentityInterceptor(
            InternalJwtValidator validator,
            @Value("${grpc.service-identity.academic-to-attendance-token:}") String token) {
        this.validator = validator;
        var credentials =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                AdminGroupStatsGrpcIdentity.ATTENDANCE,
                                token)
                        .map(List::of)
                        .orElseGet(List::of);
        this.service =
                new ServiceIdentityServerInterceptor(
                        AdminGroupStatsGrpcIdentity.ATTENDANCE,
                        Map.of(
                                AdminGroupStatsGrpcIdentity.METHOD,
                                ServicePrincipal.ACADEMIC_SERVICE),
                        credentials);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        if (!AdminGroupStatsGrpcIdentity.METHOD.equals(
                call.getMethodDescriptor().getFullMethodName()))
            return next.startCall(call, headers);
        try {
            String token = null;
            var supplied = headers.getAll(TOKEN);
            if (supplied != null) {
                var values = supplied.iterator();
                if (values.hasNext()) token = values.next();
                if (values.hasNext()) throw new InternalJwtException("Ambiguous internal identity");
            }
            String acceptedToken = token;
            var claims = validator.validate(acceptedToken);
            return service.interceptCall(
                    call,
                    headers,
                    (verified, metadata) ->
                            Contexts.interceptCall(
                                    Context.current()
                                            .withValue(AdminGroupStatsGrpcIdentity.CLAIMS, claims)
                                            .withValue(
                                                    AdminGroupStatsGrpcIdentity.TOKEN,
                                                    acceptedToken),
                                    verified,
                                    metadata,
                                    next));
        } catch (InternalJwtException invalid) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription("Invalid internal identity"),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
}
