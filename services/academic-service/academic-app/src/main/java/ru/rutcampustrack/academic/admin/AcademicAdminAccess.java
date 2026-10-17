package ru.rutcampustrack.academic.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.security.admin.*;

/** Request adapter; mutation entry points call this before taking any user locks. */
@Component
public class AcademicAdminAccess {
    private final AdminAccessJdbcAuthority authority;
    private final RequestContext context;
    private final JdbcTemplate jdbc;

    public AcademicAdminAccess(
            AdminAccessJdbcAuthority authority, RequestContext context, JdbcTemplate jdbc) {
        this.authority = authority;
        this.context = context;
        this.jdbc = jdbc;
    }

    public AdminAccessProfile current() {
        return authority.current(context.getClaims());
    }

    public void require(String... permissions) {
        authority.require(context.getClaims(), permissions);
    }

    public void requireFull() {
        authority.requireFull(context.getClaims());
    }

    public void target(long target) {
        authority.requireMutableTarget(context.getClaims(), target);
    }

    public boolean credentials(long target) {
        return authority.canReadCredentials(context.getClaims(), target);
    }

    public void mutation(Long target, String permission) {
        authority.lockGlobal();
        if (context.getUserId() == null) throw AdminAccessException.denied();
        authority.lockUsers(context.getUserId(), target);
        require(permission);
        if (target != null) target(target);
    }

    public void global() {
        authority.lockGlobal();
    }

    public Long actorId() {
        if (context.getUserId() == null) throw AdminAccessException.denied();
        return context.getUserId();
    }

    public void begin(String permission) {
        global();
        require(permission);
    }

    public void protectLast(long target) {
        authority.protectLastFull(target);
    }
}
