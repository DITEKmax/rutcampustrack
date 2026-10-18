package ru.rutcampustrack.auth.authority;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.admin.*;

import java.sql.Array;
import java.util.*;

/** Fresh Auth-only authority. No cache and no Academic SQL or callbacks. */
public class AuthAdminJdbcAuthority {
    public static final long GLOBAL_LOCK = 724830210081L;
    private final JdbcTemplate jdbc;

    public AuthAdminJdbcAuthority(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockGlobal() {
        try {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", GLOBAL_LOCK);
        } catch (DataAccessException failure) {
            throw AdminAccessException.unavailable();
        }
    }

    public void lockUsers(long actor, Long target) {
        try {
            jdbc.queryForList(
                    "SELECT id FROM auth_identities WHERE id=? OR id=? ORDER BY id FOR UPDATE",
                    Long.class,
                    actor,
                    target == null ? actor : target);
        } catch (DataAccessException failure) {
            throw AdminAccessException.unavailable();
        }
    }

    public AdminAccessProfile profile(long userId) {
        try {
            List<AdminAccessProfile> rows =
                    jdbc.query(
                            """
SELECT p.version,p.mode,p.permissions,
  (u.account_state='ACTIVE' AND u.fence_operation_id IS NULL AND g.status='active' AND p.assigned) assigned
FROM auth_identities u LEFT JOIN user_role_grants g ON g.user_id=u.id AND g.role='admin'
LEFT JOIN admin_access_profiles p ON p.user_id=u.id WHERE u.id=?
""",
                            (rs, n) -> {
                                long version = rs.getLong("version");
                                boolean assigned = rs.getBoolean("assigned");
                                String mode = rs.getString("mode");
                                Array array = rs.getArray("permissions");
                                List<String> permissions =
                                        array == null
                                                ? List.of()
                                                : Arrays.asList((String[]) array.getArray());
                                if (!assigned)
                                    return new AdminAccessProfile(
                                            userId, false, null, List.of(), version);
                                // Never interpret missing/malformed durable state as FULL.
                                if (!("FULL".equals(mode) || "LIMITED".equals(mode)))
                                    throw AdminAccessException.denied();
                                List<String> validated;
                                try {
                                    validated = AdminPermission.validate(mode, permissions);
                                } catch (AdminAccessException invalid) {
                                    throw AdminAccessException.denied();
                                }
                                return new AdminAccessProfile(
                                        userId,
                                        true,
                                        mode,
                                        "FULL".equals(mode)
                                                ? AdminPermission.allCodes()
                                                : validated,
                                        version);
                            },
                            userId);
            if (rows.isEmpty())
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Пользователь не найден");
            return rows.getFirst();
        } catch (DataAccessException failure) {
            throw AdminAccessException.unavailable();
        }
    }

    public AdminAccessProfile current(InternalJwtClaims claims) {
        if (claims == null
                || !"ADMIN".equals(claims.role())
                || !"ACTIVE".equals(claims.status())
                || claims.readOnly()) throw AdminAccessException.denied();
        try {
            Boolean live =
                    jdbc.queryForObject(
                            """
SELECT EXISTS(SELECT 1 FROM auth_identities u
  JOIN auth_sessions s ON s.user_id=u.id
  JOIN user_role_grants g ON g.id=s.active_role_grant_id AND g.user_id=u.id
  WHERE u.id=? AND u.roles_version=? AND s.sid=? AND s.session_version=?
    AND u.account_state='ACTIVE' AND u.fence_operation_id IS NULL
    AND s.revoked_at IS NULL AND s.refresh_expires_at>NOW()
    AND g.role='admin' AND g.status='active')
""",
                            Boolean.class,
                            claims.userId(),
                            claims.rolesVersion(),
                            claims.sessionId(),
                            claims.sessionVersion());
            if (!Boolean.TRUE.equals(live)) throw AdminAccessException.denied();
            AdminAccessProfile profile = profile(claims.userId());
            if (!profile.assigned()) throw AdminAccessException.denied();
            return profile;
        } catch (DataAccessException failure) {
            throw AdminAccessException.unavailable();
        }
    }

    public void require(InternalJwtClaims claims, String... permissions) {
        AdminAccessProfile current = current(claims);
        for (String permission : permissions)
            if (!current.allows(permission)) throw AdminAccessException.denied();
    }

    public void requireFull(InternalJwtClaims claims) {
        if (!current(claims).full()) throw AdminAccessException.fullRequired();
    }

    public boolean activeAdmin(long target) {
        try {
            return Boolean.TRUE.equals(
                    jdbc.queryForObject(
                            """
SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id=? AND role='admin' AND status='active')
""",
                            Boolean.class,
                            target));
        } catch (DataAccessException failure) {
            throw AdminAccessException.unavailable();
        }
    }

    public void requireMutableTarget(InternalJwtClaims claims, long target) {
        if (!current(claims).full() && activeAdmin(target))
            throw new AdminAccessException(HttpStatus.FORBIDDEN, "ADMIN_TARGET_PROTECTED");
    }

    public boolean canReadCredentials(InternalJwtClaims claims, long target) {
        AdminAccessProfile current = current(claims);
        return current.allows("USER_CREDENTIALS_MANAGE")
                && (current.full() || !activeAdmin(target));
    }

    /** Caller holds global lock before any users locks. */
    public void protectLastFull(long target) {
        AdminAccessProfile profile = profile(target);
        if (profile.full()
                && jdbc.queryForObject(
                                """
SELECT COUNT(*) FROM admin_access_profiles p JOIN auth_identities u ON u.id=p.user_id
  JOIN user_role_grants g ON g.user_id=p.user_id AND g.role='admin'
WHERE p.assigned AND p.mode='FULL' AND g.status='active' AND u.account_state='ACTIVE' AND u.fence_operation_id IS NULL
""",
                                Long.class)
                        <= 1L)
            throw new AdminAccessException(HttpStatus.CONFLICT, "LAST_FULL_ADMIN");
    }
}
