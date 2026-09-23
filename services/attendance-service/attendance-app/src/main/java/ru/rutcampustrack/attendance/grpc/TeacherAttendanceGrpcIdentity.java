package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Context-bound identity for the additive teacher attendance read boundary. */
final class TeacherAttendanceGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("teacher-attendance-internal-jwt-claims");
    static final Context.Key<String> SIGNED_TOKEN =
            Context.key("teacher-attendance-signed-token");

    private TeacherAttendanceGrpcIdentity() {
    }

    static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException("Internal teacher identity is missing");
        }
        return claims;
    }

    static String requireToken() {
        String token = SIGNED_TOKEN.get();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Signed teacher token is missing");
        }
        return token;
    }
}
