package ru.rutcampustrack.auth.authority;

import io.grpc.*;

import org.springframework.stereotype.Component;

/** Transient metadata only. The first accepted command verifies it; no bearer is persisted. */
@Component
public final class AuthActorMetadataInterceptor implements ServerInterceptor {
    static final Context.Key<String> ACTOR_TOKEN = Context.key("rct-auth-directed-actor-token");
    private static final Metadata.Key<String> HEADER =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, S> ServerCall.Listener<Q> interceptCall(
            ServerCall<Q, S> call, Metadata headers, ServerCallHandler<Q, S> next) {
        String token = null;
        Iterable<String> values = headers.getAll(HEADER);
        if (values != null)
            for (String value : values) {
                if (token != null) {
                    call.close(
                            Status.UNAUTHENTICATED.withDescription("one actor identity required"),
                            new Metadata());
                    return new ServerCall.Listener<>() {};
                }
                token = value;
            }
        return Contexts.interceptCall(
                Context.current().withValue(ACTOR_TOKEN, token), call, headers, next);
    }
}
