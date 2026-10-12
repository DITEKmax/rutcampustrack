package ru.rutcampustrack.auth.export;

import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

/** One live registry rule for legacy capabilities and durable exports. */
public final class RegistryExportAdmission {
    private RegistryExportAdmission() {}

    public static AdminAccessProfile requireCurrent(AdminAccessJdbcAuthority admins,
            SessionPrincipal principal, ReportDownloadKind kind) {
        String permission = switch (kind) {
            case ADMIN_USERS -> "USERS_VIEW";
            case ADMIN_GROUPS -> "GROUPS_VIEW";
            default -> null;
        };
        if (permission == null) return null;
        AdminAccessProfile profile = admins.current(claims(principal));
        if (!profile.allows(permission)) throw AdminAccessException.denied();
        return profile;
    }

    static InternalJwtClaims claims(SessionPrincipal principal) {
        if (principal.selectedRole() == null || principal.selectedStatus() == null) {
            throw AdminAccessException.denied();
        }
        return new InternalJwtClaims(principal.userId(), principal.sessionId(),
                principal.sessionVersion(), principal.rolesVersion(), principal.selectedRole().name(),
                principal.selectedStatus().name(), principal.groupId(), principal.headman(), principal.readOnly());
    }
}
