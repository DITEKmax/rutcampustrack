package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import io.grpc.Status;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

final class AdminActivityGrpcIdentity {
    static final Context.Key<InternalJwtClaims> CLAIMS = Context.key("admin-activity-claims");
    static final Context.Key<String> TOKEN = Context.key("admin-activity-signed-token");

    private AdminActivityGrpcIdentity() {}

    static String requireAdminToken() {
        var claims = CLAIMS.get();
        String token = TOKEN.get();
        if (claims == null || token == null) throw Status.UNAUTHENTICATED.asRuntimeException();
        if (!"ADMIN".equals(claims.role())
                || !"ACTIVE".equals(claims.status())
                || claims.readOnly()) {
            throw Status.PERMISSION_DENIED
                    .withDescription("DASHBOARD_VIEW is required")
                    .asRuntimeException();
        }
        return token;
    }
}
