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

    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient identityData;

    public AcademicAdminAccess(
            AdminAccessJdbcAuthority authority, RequestContext context, JdbcTemplate jdbc) {
        this.authority = authority;
        this.context = context;
        this.jdbc = jdbc;
    }

    public AdminAccessProfile current() {
        var actor = context.getClaims();
        var profile = context.getAdmittedAdminProfile();
        if (actor == null
                || !"ADMIN".equals(actor.role())
                || actor.readOnly()
                || !"ACTIVE".equals(actor.status())
                || profile == null
                || !profile.assigned()
                || profile.userId() != actor.userId()) throw AdminAccessException.denied();
        return profile;
    }

    public void require(String... permissions) {
        var profile = current();
        for (String permission : permissions)
            if (!profile.full() && !profile.allows(permission)) throw AdminAccessException.denied();
    }

    public void requireFull() {
        if (!current().full()) throw AdminAccessException.fullRequired();
    }

    public void target(long target) {
        authority.requireMutableTarget(context.getClaims(), target);
    }

    public boolean credentials(long target) {
        try {
            var result =
                    identityData.initialCredentials(
                            java.util.List.of(target),
                            System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3));
            return result.getCredentialsCount() == 1
                    && result.getCredentials(0).getUserId() == target;
        } catch (io.grpc.StatusRuntimeException denied) {
            if (denied.getStatus().getCode() == io.grpc.Status.Code.PERMISSION_DENIED) return false;
            throw denied;
        }
    }

    public void mutation(Long target, String permission) {
        global();
        if (context.getUserId() == null) throw AdminAccessException.denied();
        jdbc.queryForList(
                "SELECT id FROM academic_profiles WHERE id=? OR id=? ORDER BY id FOR UPDATE",
                Long.class,
                context.getUserId(),
                target == null ? context.getUserId() : target);
        require(permission);
        if (target != null) target(target);
    }

    public void global() {
        // This is an own-DB domain serialization lock, never Auth session/profile authority.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", AdminAccessJdbcAuthority.GLOBAL_LOCK);
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
