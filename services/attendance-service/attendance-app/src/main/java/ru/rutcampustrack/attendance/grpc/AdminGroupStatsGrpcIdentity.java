package ru.rutcampustrack.attendance.grpc;

import io.grpc.Context;
import io.grpc.Status;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

final class AdminGroupStatsGrpcIdentity {
    static final String METHOD =
            "rutcampustrack.attendance.AttendanceAdminGroupStatsGrpcService/GetGroupSemesterStats";
    static final ServicePrincipal ATTENDANCE = new ServicePrincipal("attendance-service");
    static final Context.Key<InternalJwtClaims> CLAIMS = Context.key("admin-group-stats-claims");
    static final Context.Key<String> TOKEN = Context.key("admin-group-stats-token");

    private AdminGroupStatsGrpcIdentity() {}

    static String requireAdminToken() {
        var claims = CLAIMS.get();
        String token = TOKEN.get();
        if (claims == null
                || token == null
                || !ServicePrincipal.ACADEMIC_SERVICE.equals(ServicePrincipal.CONTEXT_KEY.get()))
            throw Status.UNAUTHENTICATED.asRuntimeException();
        if (!"ADMIN".equals(claims.role())
                || !"ACTIVE".equals(claims.status())
                || claims.readOnly())
            throw Status.PERMISSION_DENIED
                    .withDescription("DASHBOARD_VIEW is required")
                    .asRuntimeException();
        return token;
    }
}
