package ru.rutcampustrack.academic.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Repository
public class UserArchiveRepository {
    private final JdbcTemplate jdbc;
    public UserArchiveRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void lock(long owner, long target, UUID operation) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", operation.toString());
        // Global user -> group order matches transfer/headman mutations. NO KEY
        // UPDATE still serializes user changes but permits helper INSERT's FK
        // KEY SHARE while that writer owns the group. Archive upgrades the target
        // lock only after the group is ours, avoiding a user-FK/group lock cycle.
        List<Long> lockedUsers = jdbc.queryForList("""
                SELECT u.id FROM users u WHERE u.id=? OR EXISTS (SELECT 1 FROM user_role_grants g
                  WHERE g.user_id=u.id AND g.role='admin' AND g.status='active')
                ORDER BY u.id FOR NO KEY UPDATE
                """, Long.class, target);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM users u JOIN user_role_grants g ON g.user_id=u.id
                  WHERE u.id=? AND u.status<>'archived' AND g.role='admin' AND g.status='active')
                """, Boolean.class, owner))) denied();
        if (!lockedUsers.contains(target)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        jdbc.queryForList("""
                SELECT id FROM groups WHERE id IN (
                  SELECT group_id FROM user_role_grants WHERE user_id=? AND group_id IS NOT NULL
                  UNION SELECT group_id FROM headman_assistants WHERE student_id=?
                ) ORDER BY id FOR UPDATE
                """,Long.class,target,target);
    }

    public Local observe(long target) {
        String state = jdbc.queryForObject("""
                SELECT jsonb_build_object('user', to_jsonb(u)-'password_hash'-'initial_password'-'email'-'phone',
                    'grants', COALESCE((SELECT jsonb_agg(to_jsonb(g) ORDER BY g.id)
                       FROM user_role_grants g WHERE g.user_id=u.id), '[]'::jsonb))::text
                FROM users u WHERE u.id=?
                """, String.class, target);
        if (state == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
        // No private user state is persisted or returned; only its digest seals local authority.
        String status = jdbc.queryForObject("SELECT status::text FROM users WHERE id=?", String.class, target);
        long groups = jdbc.queryForObject("""
                SELECT COUNT(*) FROM (
                  SELECT group_id FROM student_group_history WHERE user_id=?
                  UNION SELECT group_id FROM user_role_grants WHERE user_id=? AND group_id IS NOT NULL
                  UNION SELECT group_id FROM assignments WHERE teacher_id=?
                ) linked WHERE group_id IS NOT NULL
                """, Long.class, target, target, target);
        long headman = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT group_id) FROM user_role_grants
                WHERE user_id=? AND role='headman' AND status='active'
                """, Long.class, target);
        boolean dangerous = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id=?
                    AND role IN ('teacher','headman') AND status='active')
                """, Boolean.class, target));
        // Warning concerns effective assignments on the observed Moscow date.
        long sole = jdbc.queryForObject("""
                SELECT COUNT(*) FROM assignments a JOIN semesters s ON s.id=a.semester_id
                WHERE a.teacher_id=? AND a.lifecycle_state='ACTIVE'
                  AND a.valid_from <= (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date
                  AND COALESCE(a.valid_until_exclusive,s.date_to+1) > (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date
                  AND NOT EXISTS (SELECT 1 FROM assignments other JOIN user_role_grants grant_row
                    ON grant_row.user_id=other.teacher_id AND grant_row.role='teacher' AND grant_row.status='active'
                    JOIN users teacher ON teacher.id=other.teacher_id AND teacher.status<>'archived'
                    WHERE other.teacher_id<>a.teacher_id AND other.subject_id=a.subject_id
                      AND other.group_id=a.group_id AND other.semester_id=a.semester_id
                      AND other.lesson_type=a.lesson_type AND other.lifecycle_state='ACTIVE'
                      AND other.valid_from <= (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date
                      AND COALESCE(other.valid_until_exclusive,s.date_to+1) > (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::date)
                """, Long.class, target);
        String fingerprint = digest("academic-user-impact-v2:"+state+":"+groups+":"+headman+":"+sole+":"+dangerous);
        return new Local(target, fingerprint, status, groups, headman, sole, dangerous, Instant.now());
    }

    public void savePreview(long owner, Local local, String digest, Instant expiry) {
        jdbc.update("""
                INSERT INTO user_archive_preview(preview_digest,owner_id,target_id,academic_digest,requires_password,expires_at)
                VALUES (?,?,?,?,?,?)
                """, digest, owner, local.userId(), local.digest(), local.requiresPassword(),
                java.sql.Timestamp.from(expiry));
        // Only expired transient previews are pruned. Durable receipts remain.
        jdbc.update("DELETE FROM user_archive_preview WHERE expires_at < NOW()");
    }

    public void validatePreview(long owner, long target, String digest, Local local) {
        List<String> fingerprints = jdbc.queryForList("""
                SELECT academic_digest FROM user_archive_preview WHERE preview_digest=?
                  AND owner_id=? AND target_id=? AND expires_at > NOW()
                """, String.class, digest, owner, target);
        if (fingerprints.size() != 1 || !fingerprints.getFirst().equals(local.digest())) {
            conflict("archive_preview_stale");
        }
    }

    public Receipt receipt(UUID operation) {
        List<Receipt> rows = jdbc.query("""
                SELECT owner_id,target_id,action,preview_digest,requires_password
                FROM user_archive_receipt WHERE operation_id=?
                """, (rs, n) -> new Receipt(rs.getLong(1), rs.getLong(2), rs.getString(3),
                    rs.getString(4), rs.getBoolean(5)), operation);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void saveReceipt(UUID operation, long owner, long target, String action, String digest, boolean proof) {
        jdbc.update("""
                INSERT INTO user_archive_receipt(operation_id,owner_id,target_id,action,preview_digest,requires_password)
                VALUES (?,?,?,?,?,?)
                """, operation, owner, target, action, digest, proof);
    }

    public void checkArchiveSafeguards(long owner, long target) {
        if (owner == target) denied();
        boolean targetAdmin = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE user_id=? AND role='admin' AND status='active')
                """, Boolean.class, target));
        if (targetAdmin && jdbc.queryForObject("""
                SELECT COUNT(DISTINCT g.user_id) FROM user_role_grants g JOIN users u ON u.id=g.user_id
                WHERE g.role='admin' AND g.status='active' AND u.status<>'archived'
                """, Long.class) <= 1L) conflict("last_admin_archive_denied");
    }

    public void restore(long target) {
        // No grant, membership, helper grant or selected session is resurrected.
        jdbc.update("UPDATE users SET status='active',group_id=NULL,is_headman=FALSE,updated_at=NOW() WHERE id=?", target);
        jdbc.update("UPDATE user_role_grants SET status='archived',updated_at=NOW() WHERE user_id=? AND status<>'archived'",target);
        jdbc.update("UPDATE user_role_grants SET group_id=NULL,updated_at=NOW() WHERE user_id=? AND role='student' AND group_id IS NOT NULL",target);
        jdbc.update("""
                UPDATE auth_sessions SET active_role_grant_id=NULL,session_version=session_version+1
                WHERE user_id=? AND revoked_at IS NULL AND active_role_grant_id IS NOT NULL
                """, target);
    }

    public List<Long> cacheGroups(long target) {
        return jdbc.queryForList("""
                SELECT group_id FROM user_role_grants WHERE user_id=? AND group_id IS NOT NULL
                UNION SELECT group_id FROM headman_assistants WHERE student_id=?
                """,Long.class,target,target);
    }
    public void revokeHelpers(long target) {
        jdbc.update("UPDATE headman_assistants SET is_active=FALSE,revoked_at=NOW() WHERE student_id=? AND is_active",target);
    }
    public void revokeHeadmanHelpers(long target) {
        jdbc.update("""
                UPDATE headman_assistants SET is_active=FALSE,revoked_at=NOW() WHERE is_active
                AND group_id IN (SELECT group_id FROM user_role_grants
                   WHERE user_id=? AND role='headman' AND status='active')
                """,target);
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static void conflict(String reason) { throw new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private static void denied() { throw new ResponseStatusException(HttpStatus.FORBIDDEN, "user_archive_denied"); }
    public record Local(long userId, String digest, String status, long groupCount, long headmanCount,
            long soleTeacherCount, boolean requiresPassword, Instant observedAt) { }
    public record Receipt(long ownerId, long targetId, String action, String digest, boolean requiresPassword) {
        public void check(long owner, long target, String requestedAction, String requestedDigest) {
            if (owner != ownerId || target != targetId || !action.equals(requestedAction)
                    || !java.util.Objects.equals(digest, requestedDigest)) conflict("operation_id_conflict");
        }
    }
}
