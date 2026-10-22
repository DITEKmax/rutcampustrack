package ru.rutcampustrack.auth.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.auth.authority.AuthAdminJdbcAuthority;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;

/** Uses Auth-owned live authority with the identity-before-session lock order. */
@Component
public class AuthAdminAccess {
    private final AuthAdminJdbcAuthority authority;

    public AuthAdminAccess(JdbcTemplate jdbc) {
        this.authority = new AuthAdminJdbcAuthority(jdbc);
    }

    /** Fresh read for an existing trusted socket; no mutation locks or success cache. */
    public void requireDashboardSession(SessionPrincipal principal) {
        authority.require(claims(principal), "DASHBOARD_VIEW");
    }

    public void authorize(SessionPrincipal principal, Long target, String... permissions) {
        authority.lockGlobal();
        authority.lockUsers(principal.userId(), target);
        var claims = claims(principal);
        authority.require(claims, permissions);
        if (target != null) authority.requireMutableTarget(claims, target);
    }

    private InternalJwtClaims claims(SessionPrincipal p) {
        if (p.selectedRole() == null || p.selectedStatus() == null)
            throw AdminAccessException.denied();
        return new InternalJwtClaims(
                p.userId(),
                p.sessionId(),
                p.sessionVersion(),
                p.rolesVersion(),
                p.selectedRole().name(),
                p.selectedStatus().name(),
                p.groupId(),
                p.headman(),
                p.readOnly());
    }
}
