package ru.rutcampustrack.academic.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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

    private static final String SELECT_GRANT_STATUS_SQL = """
            SELECT status
            FROM user_role_grants
            WHERE user_id = ? AND role = ?
            """;

    private static final String SELECT_ACTIVE_STUDENT_GROUP_SQL = """
            SELECT g.group_id
            FROM user_role_grants g
            JOIN users u ON u.id = g.user_id
            WHERE g.user_id = ? AND g.role = 'student' AND g.status = 'active'
              AND g.group_id IS NOT NULL AND u.status <> 'archived'
            ORDER BY g.group_id
            LIMIT 1
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
        String baseRole = user.getRole().name().toLowerCase(Locale.ROOT);
        String baseStatus = synchronizedBaseStatus(userId, baseRole, grantStatus(user.getStatus()));
        upsertGrant(userId, baseRole, baseStatus, user.getGroupId(), now);

        Optional<Long> activeStudentGroup = activeStudentGrantGroup(userId);
        boolean activeHeadman = activeStudentGroup.isPresent() && user.isHeadman();
        if (activeHeadman) {
            upsertGrant(userId, "headman", "active", activeStudentGroup.get(), now);
        } else if (activeStudentGroup.isPresent() && user.isHeadman()) {
            // A non-active account must never keep active headman authority.
            upsertGrant(userId, "headman", "suspended", activeStudentGroup.get(), now);
        } else {
            // Keep the durable row for audit/session foreign keys; revoke only
            // its selectable authority and preserve its former group scope.
            jdbcTemplate.update(SUSPEND_HEADMAN_SQL, now, userId);
        }
    }

    /**
     * Atomically writes one managed base-role grant while leaving every other
     * grant row untouched. The caller owns the surrounding user transaction;
     * this method takes the same user lock as Auth's session authority.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void upsertRole(Long userId, String role, String status, Long groupId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        jdbcTemplate.queryForObject(LOCK_USER_SQL, Long.class, userId);
        upsertGrant(userId, role.toLowerCase(Locale.ROOT), status.toLowerCase(Locale.ROOT),
                groupId, OffsetDateTime.now());
    }

    /**
     * Keeps derived HEADMAN authority aligned when the STUDENT grant changes.
     * The requested group argument is retained for existing callers, but the
     * durable active STUDENT grant is the only source for the derived scope.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void synchronizeDerivedHeadman(Long userId, boolean shouldBeActive, Long requestedGroupId) {
        Objects.requireNonNull(userId, "userId");
        jdbcTemplate.queryForObject(LOCK_USER_SQL, Long.class, userId);
        Optional<Long> activeStudentGroup = activeStudentGrantGroup(userId);
        if (shouldBeActive && activeStudentGroup.isPresent()) {
            upsertGrant(userId, "headman", "active", activeStudentGroup.get(), OffsetDateTime.now());
        } else {
            jdbcTemplate.update(SUSPEND_HEADMAN_SQL, OffsetDateTime.now(), userId);
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

    private String synchronizedBaseStatus(Long userId, String role, String desiredStatus) {
        if (!"active".equals(desiredStatus)) return desiredStatus;
        List<String> existing = jdbcTemplate.query(
                SELECT_GRANT_STATUS_SQL,
                ps -> {
                    ps.setLong(1, userId);
                    ps.setString(2, role);
                },
                (rs, rowNum) -> rs.getString(1));
        return existing == null || existing.isEmpty() ? desiredStatus : existing.get(0);
    }

    private Optional<Long> activeStudentGrantGroup(Long userId) {
        List<Long> groups = jdbcTemplate.query(
                SELECT_ACTIVE_STUDENT_GROUP_SQL,
                ps -> ps.setLong(1, userId),
                (rs, rowNum) -> rs.getLong(1));
        return groups.stream().findFirst();
    }

    private static String grantStatus(AccountStatus status) {
        return Objects.requireNonNull(status, "user.status").name().toLowerCase(Locale.ROOT);
    }
}
