package ru.rutcampustrack.attendance.grpc;

import io.grpc.*;
import java.nio.file.*;
import java.util.*;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import ru.rutcampustrack.constructor.grpc.ConstructorAttendanceParticipantGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

/** The two constructor mutations accept only the dedicated Schedule TLS capability. */
@GrpcGlobalServerInterceptor
public final class ConstructorAttendanceIdentityInterceptor implements ServerInterceptor {
    static final Context.Key<String> ACTOR_TOKEN = Context.key("constructor-attendance-actor-token");
    private final ServiceIdentityServerInterceptor delegate;

    public ConstructorAttendanceIdentityInterceptor(@Value("${constructor.rpc.schedule-to-attendance-token-file:}") String tokenFile) {
        String token = "";
        if (tokenFile != null && !tokenFile.isBlank()) {
            try { token = Files.readString(Path.of(tokenFile)).strip(); }
            catch (java.io.IOException | RuntimeException unavailable) { token = ""; }
        }
        var attendance = new ServicePrincipal("attendance-service");
        delegate = new ServiceIdentityServerInterceptor(attendance, Map.of(
                ConstructorAttendanceParticipantGrpc.getApplyDeletionMethod().getFullMethodName(), ServicePrincipal.SCHEDULE_SERVICE,
                ConstructorAttendanceParticipantGrpc.getMaterializePastMethod().getFullMethodName(), ServicePrincipal.SCHEDULE_SERVICE),
                DirectedServiceCredential.tryCreate(ServicePrincipal.SCHEDULE_SERVICE, attendance, token).map(List::of).orElseGet(List::of));
    }

    @Override public <Q,S> ServerCall.Listener<Q> interceptCall(ServerCall<Q,S> call, Metadata headers, ServerCallHandler<Q,S> next) {
        return delegate.interceptCall(call, headers, (verified, metadata) -> {
            if (!ConstructorAttendanceParticipantGrpc.SERVICE_NAME.equals(call.getMethodDescriptor().getServiceName())) return next.startCall(verified, metadata);
            Iterable<String> values = metadata.getAll(StudentGrpcIdentityInterceptor.INTERNAL_TOKEN);
            String token = null;
            if (values != null) for (String value : values) {
                if (token != null || value == null || value.isBlank()) {
                    call.close(Status.UNAUTHENTICATED.withDescription("Ambiguous internal identity"), new Metadata());
                    return new ServerCall.Listener<>() {};
                }
                token = value;
            }
            return Contexts.interceptCall(Context.current().withValue(ACTOR_TOKEN, token), verified, metadata, next);
        });
    }
}
