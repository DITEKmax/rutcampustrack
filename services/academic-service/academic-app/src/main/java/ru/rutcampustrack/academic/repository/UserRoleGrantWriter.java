package ru.rutcampustrack.academic.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;

/**
 * Transactional writer for the V24 authority rows owned by the Academic user
 * mutation flow. The Auth service remains the reader; this writer preserves
 * the grant row identity and revokes authority through status changes.
 */
@Repository
public class UserRoleGrantWriter {

    private static final String LOCK_USER_SQL = """
            SELECT id
            FROM users
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String UPSERT_GRANT_SQL = """
            INSERT INTO user_role_grants (
                user_id, role, status, group_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, role) DO UPDATE
            SET status = EXCLUDED.status,
                group_id = EXCLUDED.group_id,
                updated_at = EXCLUDED.updated_at
            """;

    private static final String SUSPEND_HEADMAN_SQL = """
            UPDATE user_role_grants
            SET status = 'suspended', updated_at = ?
            WHERE user_id = ? AND role = 'headman'
            """;

    private static final String ARCHIVE_GRANTS_SQL = """
            UPDATE user_role_grants
            SET status = 'archived', updated_at = ?
            WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserRoleGrantWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    /**
     * Synchronizes only the user's base role and optional headman role.
     * Existing grants for other roles remain untouched. The mandatory
     * transaction and explicit user lock keep the write in the same lock order
     * as the Auth session authority.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronize(User user) {
        Objects.requireNonNull(user, "user");
        Long userId = Objects.requireNonNull(user.getId(), "user.id");
        jdbcTemplate.queryForObject(LOCK_USER_SQL, Long.class, userId);

        OffsetDateTime now = OffsetDateTime.now();
        upsertGrant(userId,
                user.getRole().name().toLowerCase(Locale.ROOT),
                grantStatus(user.getStatus()),
                user.getGroupId(),
                now);

        boolean activeHeadman = user.getRole() == UserRole.STUDENT
                && user.isHeadman()
                && user.getStatus() == AccountStatus.ACTIVE;
        if (activeHeadman) {
            upsertGrant(userId, "headman", "active", user.getGroupId(), now);
        } else if (user.getRole() == UserRole.STUDENT && user.isHeadman()) {
            // A non-active account must never keep active headman authority.
            upsertGrant(userId, "headman", "suspended", user.getGroupId(), now);
        } else {
            // Keep the durable row for audit/session foreign keys; revoke only
            // its selectable authority and preserve its former group scope.
            jdbcTemplate.update(SUSPEND_HEADMAN_SQL, now, userId);
        }
    }

    /**
     * Archives every retained grant for an account. Archived grants remain
     * durable for session foreign keys and audit/history, but none remains
     * ACTIVE because Auth has no separate global account-status gate.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void archive(User user) {
        Objects.requireNonNull(user, "user");
        Long userId = Objects.requireNonNull(user.getId(), "user.id");
        jdbcTemplate.queryForObject(LOCK_USER_SQL, Long.class, userId);
        OffsetDateTime now = OffsetDateTime.now();
        upsertGrant(userId,
                user.getRole().name().toLowerCase(Locale.ROOT),
                "archived",
                user.getGroupId(),
                now);
        jdbcTemplate.update(ARCHIVE_GRANTS_SQL, now, userId);
    }

    private void upsertGrant(Long userId,
                             String role,
                             String status,
                             Long groupId,
                             OffsetDateTime now) {
        jdbcTemplate.update(UPSERT_GRANT_SQL, userId, role, status, groupId, now, now);
    }

    private static String grantStatus(AccountStatus status) {
        return Objects.requireNonNull(status, "user.status").name().toLowerCase(Locale.ROOT);
    }
}
