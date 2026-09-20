package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Context-bound identity for the student homework mutation RPC. */
final class StudentHomeworkGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS = Context.key("student-homework-internal-jwt-claims");

    private StudentHomeworkGrpcIdentity() {
    }

    static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException("Internal identity is missing");
        }
        return claims;
    }
}
