package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Context-bound identity for the additive teacher academic read boundary. */
final class TeacherAcademicGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("teacher-academic-internal-jwt-claims");

    private TeacherAcademicGrpcIdentity() {
    }

    static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException("Internal teacher identity is missing");
        }
        return claims;
    }
}
