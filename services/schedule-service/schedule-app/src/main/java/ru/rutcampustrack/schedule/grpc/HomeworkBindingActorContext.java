package ru.rutcampustrack.schedule.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/**
 * The authenticated Academic actor for the homework-binding RPCs.
 *
 * <p>The actor is carried in trusted gRPC metadata.  It is deliberately kept
 * outside the protobuf request: request payloads are replayable data and must
 * not be allowed to choose the authority that owns a binding.  The context is
 * populated only after the gRPC interceptor validates the signed Internal JWT;
 * it never stores caller-controlled actor headers.</p>
 */
public final class HomeworkBindingActorContext {

    static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("homework-binding-actor-claims");

    private HomeworkBindingActorContext() {
    }

    public static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null || claims.userId() <= 0) {
            throw new IllegalArgumentException("authenticated homework-binding identity is required");
        }
        return claims;
    }

    public static long requireActorId() {
        return requireClaims().userId();
    }
}
