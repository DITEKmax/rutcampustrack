package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;

import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Context-bound identity for the student homework mutation RPC. */
final class StudentHomeworkGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS =
            Context.key("student-homework-internal-jwt-claims");
    static final Context.Key<String> ADMIN_PERMISSION_TOKEN =
            Context.key("academic-admin-permission-signed-actor");

    private StudentHomeworkGrpcIdentity() {}

    static InternalJwtClaims requireClaims() {
        InternalJwtClaims claims = CLAIMS.get();
        if (claims == null) {
            throw new IllegalStateException("Internal identity is missing");
        }
        return claims;
    }

    static String requireAdminPermissionToken() {
        requireClaims();
        String token = ADMIN_PERMISSION_TOKEN.get();
        if (token == null || token.isBlank())
            throw new IllegalStateException("Signed admin permission actor is missing");
        return token;
    }
}
