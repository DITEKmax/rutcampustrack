package ru.rutcampustrack.auth.security;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;
/** Uses the same durable database as Academic, with the same lock order. */
@Component
public class AuthAdminAccess {
    private final AdminAccessJdbcAuthority authority;
    private final JdbcTemplate jdbc;
    public AuthAdminAccess(JdbcTemplate jdbc) {this.jdbc=jdbc;this.authority=new AdminAccessJdbcAuthority(jdbc);}
    /** Fresh read for an existing trusted socket; no mutation locks or success cache. */
    public void requireDashboardSession(SessionPrincipal principal) {
        authority.require(claims(principal), "DASHBOARD_VIEW");
    }
    public void authorize(SessionPrincipal principal,Long target,String... permissions) {
        authority.lockGlobal();
        authority.lockUsers(principal.userId(),target);
        var claims=claims(principal);
        authority.require(claims,permissions);
        if(target!=null) authority.requireMutableTarget(claims,target);
    }
    private InternalJwtClaims claims(SessionPrincipal p) {
        if(p.selectedRole()==null || p.selectedStatus()==null) throw AdminAccessException.denied();
        return new InternalJwtClaims(p.userId(),p.sessionId(),p.sessionVersion(),p.rolesVersion(),
          p.selectedRole().name(),p.selectedStatus().name(),p.groupId(),p.headman(),p.readOnly());
    }
}
