package ru.rutcampustrack.academic.constructor;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.beans.factory.annotation.Value;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServiceIdentityServerInterceptor;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

/** The dedicated Schedule pair is the accepted-command recovery authority, never a shared token. */
@GrpcGlobalServerInterceptor
public class ConstructorServiceIdentityInterceptor implements ServerInterceptor {
    static final String METHOD =
            "rutcampustrack.constructor.ConstructorAcademicParticipant/ApplyDeletion";
    static final Context.Key<InternalJwtClaims> ACTOR = Context.key("constructor-academic-actor");
    private static final Metadata.Key<String> TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final ServiceIdentityServerInterceptor delegate;
    private final InternalJwtValidator validator;

    public ConstructorServiceIdentityInterceptor(
            @Value("${constructor.rpc.schedule-to-academic-token-file:}") String file,
            InternalJwtValidator validator) {
        this.validator = validator;
        delegate = new ServiceIdentityServerInterceptor(
                ServicePrincipal.ACADEMIC_SERVICE,
                Map.of(METHOD, ServicePrincipal.SCHEDULE_SERVICE),
                DirectedServiceCredential.tryCreate(ServicePrincipal.SCHEDULE_SERVICE,
                        ServicePrincipal.ACADEMIC_SERVICE, load(file)).map(List::of).orElseGet(List::of));
    }

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        if (!METHOD.equals(call.getMethodDescriptor().getFullMethodName()))
            return next.startCall(call, headers);
        return delegate.interceptCall(call, headers, (verifiedCall, verifiedHeaders) -> {
            Iterable<String> tokens = verifiedHeaders.getAll(TOKEN);
            if (tokens == null) return next.startCall(verifiedCall, verifiedHeaders);
            var values = new java.util.ArrayList<String>();
            tokens.forEach(values::add);
            try {
                if (values.size() != 1) throw new IllegalArgumentException();
                InternalJwtClaims claims = validator.validate(values.getFirst());
                return Contexts.interceptCall(Context.current().withValue(ACTOR, claims),
                        verifiedCall, verifiedHeaders, next);
            } catch (RuntimeException invalid) {
                verifiedCall.close(Status.UNAUTHENTICATED.withDescription("valid signed constructor actor required"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
        });
    }

    private static String load(String file) {
        if (file == null || file.isBlank()) return "";
        try {
            if (Files.size(Path.of(file)) > 128) throw new IllegalArgumentException();
            String token = Files.readString(Path.of(file), StandardCharsets.US_ASCII).strip();
            if (!DirectedServiceCredential.isCanonicalToken(token)) throw new IllegalArgumentException();
            return token;
        } catch (Exception invalid) {
            throw new IllegalStateException("Invalid dedicated constructor credential configuration");
        }
    }
}
