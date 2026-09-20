package ru.rutcampustrack.academic.studentprojection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL read adapter for the signed student projection.
 *
 * <p>Authority comes from V24 grants and {@code users.roles_version}; group
 * history and V25 effective assignments are read directly with parameterized
 * SQL.  The legacy {@code users.group_id} and {@code teacher_subject_groups}
 * tables are deliberately absent from this adapter.</p>
 */
@Repository
public class JdbcStudentProjectionQueryAdapter implements StudentProjectionQuery {

    private static final String AUTHORITY_SQL = """
            SELECT u.id,
                   u.roles_version,
                   g.id AS grant_id,
                   g.role::text AS grant_role,
                   g.status::text AS grant_status,
                   g.group_id AS grant_group_id
            FROM users u
            LEFT JOIN user_role_grants g ON g.user_id = u.id
            WHERE u.id = ?
            ORDER BY g.id
            """;

    private static final String SEMESTER_SQL = """
            SELECT id, date_from, date_to
            FROM semesters
            WHERE id = ?
            """;

    private static final String SESSION_SQL = """
            SELECT sid, user_id, active_role_grant_id, session_version,
                   refresh_expires_at, revoked_at
            FROM auth_sessions
            WHERE user_id = ? AND sid = ?
            """;

    private static final String HISTORY_SQL = """
            SELECT id, user_id, group_id, joined_at, left_at
            FROM student_group_history
            WHERE user_id = ?
            ORDER BY joined_at, id
            """;

    private static final String ASSIGNMENT_SQL_PREFIX = """
            SELECT id, subject_id, group_id, semester_id,
                   lesson_type::text AS lesson_type,
                   valid_from, valid_until_exclusive
            FROM assignments
            WHERE semester_id = ?
              AND group_id IN (""";

    private static final String ASSIGNMENT_SQL_SUFFIX = """
            )
              AND valid_from < ?
              AND (valid_until_exclusive IS NULL OR valid_until_exclusive > ?)
            ORDER BY group_id, subject_id, valid_from, id
            """;

    private static final String ROSTER_SQL = """
            SELECT DISTINCT g.user_id
            FROM user_role_grants g
            JOIN users u ON u.id = g.user_id
            JOIN student_group_history h
              ON h.user_id = g.user_id
             AND h.group_id = g.group_id
            WHERE g.role = 'student'
              AND g.status = 'active'
              AND g.group_id = ?
              AND h.joined_at < ?
              AND (h.left_at IS NULL OR h.left_at > h.joined_at)
              AND (h.left_at IS NULL OR h.left_at > ?)
            ORDER BY g.user_id
            """;

    private static final String SUBJECT_SQL_PREFIX = """
            SELECT id, name, type::text AS subject_type, group_id
            FROM subjects
            WHERE id IN (""";

    private static final String SUBJECT_SQL_SUFFIX = ") ORDER BY id";

    private final JdbcTemplate jdbcTemplate;

    public JdbcStudentProjectionQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<AuthoritySnapshot> findAuthority(long userId) {
        List<AuthorityRow> rows = jdbcTemplate.query(
                AUTHORITY_SQL,
                ps -> ps.setLong(1, userId),
                JdbcStudentProjectionQueryAdapter::mapAuthorityRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        AuthorityRow first = rows.get(0);
        Map<Long, RoleGrantSnapshot> grants = new LinkedHashMap<>();
        for (AuthorityRow row : rows) {
            if (row.grantId() == null) {
                continue;
            }
            grants.put(row.grantId(), new RoleGrantSnapshot(
                    row.grantId(), row.grantRole(), row.grantStatus(), row.grantGroupId()));
        }
        List<RoleGrantSnapshot> grantSnapshots = List.copyOf(grants.values());
        String studentStatus = grantSnapshots.stream()
                .filter(grant -> "STUDENT".equalsIgnoreCase(grant.role()))
                .map(RoleGrantSnapshot::status)
                .findFirst()
                .orElse(null);
        return Optional.of(new AuthoritySnapshot(
                first.userId(), first.rolesVersion(), studentStatus, grantSnapshots));
    }

    @Override
    public Optional<SessionSnapshot> findSession(long userId, UUID sessionId) {
        List<SessionSnapshot> sessions = jdbcTemplate.query(
                SESSION_SQL,
                ps -> {
                    ps.setLong(1, userId);
                    ps.setObject(2, sessionId);
                },
                (rs, rowNum) -> new SessionSnapshot(
                        rs.getObject("sid", UUID.class),
                        rs.getLong("user_id"),
                        nullableLong(rs, "active_role_grant_id"),
                        rs.getLong("session_version"),
                        instant(rs, "refresh_expires_at"),
                        instant(rs, "revoked_at")));
        return sessions.stream().findFirst();
    }

    @Override
    public Optional<SemesterSnapshot> findSemester(long semesterId) {
        List<SemesterSnapshot> semesters = jdbcTemplate.query(
                SEMESTER_SQL,
                ps -> ps.setLong(1, semesterId),
                (rs, rowNum) -> new SemesterSnapshot(
                        rs.getLong("id"),
                        rs.getObject("date_from", LocalDate.class),
                        rs.getObject("date_to", LocalDate.class)));
        return semesters.stream().findFirst();
    }

    @Override
    public List<GroupHistorySnapshot> findGroupHistory(long userId) {
        return jdbcTemplate.query(
                HISTORY_SQL,
                ps -> ps.setLong(1, userId),
                (rs, rowNum) -> new GroupHistorySnapshot(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("group_id"),
                        rs.getObject("joined_at", LocalDate.class),
                        rs.getObject("left_at", LocalDate.class)));
    }

    @Override
    public List<AssignmentSnapshot> findAssignments(
            long semesterId,
            Collection<Long> groupIds,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive) {
        List<Long> groups = groupIds.stream()
                .distinct()
                .sorted()
                .toList();
        if (groups.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", groups.stream().map(ignored -> "?").toList());
        String sql = ASSIGNMENT_SQL_PREFIX + placeholders + ASSIGNMENT_SQL_SUFFIX;
        List<Object> args = new ArrayList<>(3 + groups.size());
        args.add(semesterId);
        args.addAll(groups);
        args.add(dateUntilExclusive);
        args.add(dateFrom);
        return jdbcTemplate.query(
                sql,
                args.toArray(),
                (rs, rowNum) -> new AssignmentSnapshot(
                        rs.getLong("id"),
                        rs.getLong("subject_id"),
                        rs.getLong("group_id"),
                        rs.getLong("semester_id"),
                        rs.getString("lesson_type"),
                        rs.getObject("valid_from", LocalDate.class),
                        rs.getObject("valid_until_exclusive", LocalDate.class)));
    }

    @Override
    public Map<Long, SubjectSnapshot> findSubjectsByIds(Collection<Long> subjectIds) {
        List<Long> ids = subjectIds.stream()
                .distinct()
                .sorted()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", ids.stream().map(ignored -> "?").toList());
        String sql = SUBJECT_SQL_PREFIX + placeholders + SUBJECT_SQL_SUFFIX;
        List<SubjectSnapshot> subjects = jdbcTemplate.query(
                sql,
                ids.toArray(),
                (rs, rowNum) -> new SubjectSnapshot(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("subject_type"),
                        rs.getLong("group_id")));
        Map<Long, SubjectSnapshot> result = new LinkedHashMap<>();
        subjects.forEach(subject -> result.put(subject.id(), subject));
        return Map.copyOf(result);
    }

    @Override
    public List<Long> findActiveStudentIds(
            long groupId,
            long semesterId,
            LocalDate dateFrom,
            LocalDate dateUntilExclusive) {
        // semesterId is part of the port contract so the caller cannot
        // accidentally reuse a roster read for another semester.  History is
        // date based; the selected semester bounds are the query's scope.
        if (semesterId <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(
                ROSTER_SQL,
                ps -> {
                    ps.setLong(1, groupId);
                    ps.setObject(2, dateUntilExclusive);
                    ps.setObject(3, dateFrom);
                },
                (rs, rowNum) -> rs.getLong("user_id"));
    }

    private static AuthorityRow mapAuthorityRow(ResultSet rs, int rowNum) throws SQLException {
        Long grantId = nullableLong(rs, "grant_id");
        return new AuthorityRow(
                rs.getLong("id"),
                rs.getLong("roles_version"),
                grantId,
                grantId == null ? null : rs.getString("grant_role"),
                grantId == null ? null : rs.getString("grant_status"),
                grantId == null ? null : nullableLong(rs, "grant_group_id"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record AuthorityRow(
            long userId,
            long rolesVersion,
            Long grantId,
            String grantRole,
            String grantStatus,
            Long grantGroupId) {
    }
}
