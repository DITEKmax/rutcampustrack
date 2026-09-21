package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Context-bound identity for the additive teacher attendance read boundary. */
final class TeacherAttendanceGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("teacher-attendance-internal-jwt-claims");

    private TeacherAttendanceGrpcIdentity() {
    }

    static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException("Internal teacher identity is missing");
        }
        return claims;
    }
}
