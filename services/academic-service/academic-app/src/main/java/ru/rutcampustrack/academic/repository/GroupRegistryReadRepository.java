package ru.rutcampustrack.academic.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read model for the ADMIN registry. Status, student count and headman are
 * resolved in one parameterized SQL projection so the page does not perform
 * per-row follow-up requests.
 */
@Repository
public class GroupRegistryReadRepository {

    private static final String HEADMAN_EXISTS = """
            EXISTS (SELECT 1 FROM user_role_grants hg
                    JOIN users hu ON hu.id = hg.user_id
                    WHERE hg.group_id = g.id AND hg.role = 'headman'
                      AND hg.status = 'active' AND hu.status <> 'archived')
            """;

    private static final String SEARCH = """
            (? = '' OR lower(g.name) LIKE ?
             OR lower(coalesce(g.alphabetic_code, '')) LIKE ?
             OR lower(coalesce(g.numeric_code, '')) LIKE ?)
            """;

    private static final String PAGE_SELECT = """
            SELECT g.id, g.name, g.alphabetic_code, g.numeric_code,
                   g.current_course, g.training_duration_years,
                   g.duration_status, g.is_active, g.created_at,
                   (SELECT count(*) FROM user_role_grants sg
                    JOIN users su ON su.id = sg.user_id
                    WHERE sg.group_id = g.id AND sg.role = 'student'
                      AND sg.status = 'active' AND su.status <> 'archived') AS student_count,
                   (SELECT nullif(trim(concat_ws(' ', hu.last_name, hu.first_name, hu.middle_name)), '')
                    FROM user_role_grants hg
                    JOIN users hu ON hu.id = hg.user_id
                    WHERE hg.group_id = g.id AND hg.role = 'headman'
                      AND hg.status = 'active' AND hu.status <> 'archived'
                    ORDER BY hu.last_name, hu.first_name, hu.id
                    LIMIT 1) AS headman_fio
            FROM groups g
            WHERE %s AND %s
            ORDER BY coalesce(g.alphabetic_code, g.name),
                     coalesce(g.numeric_code, ''), g.id
            LIMIT ? OFFSET ?
            """;

    private static final String COUNTS = """
            SELECT count(*) FILTER (WHERE g.is_active AND %s) AS active_count,
                   count(*) FILTER (WHERE g.is_active AND NOT (%s)) AS draft_count,
                   count(*) FILTER (WHERE NOT g.is_active) AS archived_count
            FROM groups g
            WHERE %s
            """;

    private final JdbcTemplate jdbcTemplate;

    public GroupRegistryReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<GroupRegistryRow> find(AdminGroupStatus status, String search, Pageable pageable) {
        String query = PAGE_SELECT.formatted(statusPredicate(status), SEARCH);
        String wildcard = normalizeSearch(search);
        List<GroupRegistryRow> rows = jdbcTemplate.query(query,
                ps -> {
                    ps.setString(1, wildcard);
                    ps.setString(2, wildcard);
                    ps.setString(3, wildcard);
                    ps.setString(4, wildcard);
                    ps.setInt(5, pageable.getPageSize());
                    ps.setLong(6, pageable.getOffset());
                }, GroupRegistryReadRepository::mapRow);
        long total = count(status, search);
        return new PageImpl<>(rows, pageable, total);
    }

    public GroupRegistryCounts countAll(String search) {
        String query = COUNTS.formatted(HEADMAN_EXISTS, HEADMAN_EXISTS, SEARCH);
        String wildcard = normalizeSearch(search);
        return jdbcTemplate.queryForObject(query,
                new Object[]{wildcard, wildcard, wildcard, wildcard},
                (rs, rowNum) -> new GroupRegistryCounts(
                        rs.getLong("active_count"),
                        rs.getLong("draft_count"),
                        rs.getLong("archived_count")));
    }

    private long count(AdminGroupStatus status, String search) {
        String query = "SELECT count(*) FROM groups g WHERE %s AND %s"
                .formatted(statusPredicate(status), SEARCH);
        String wildcard = normalizeSearch(search);
        Long value = jdbcTemplate.queryForObject(query,
                new Object[]{wildcard, wildcard, wildcard, wildcard}, Long.class);
        return value == null ? 0L : value;
    }

    private static String statusPredicate(AdminGroupStatus status) {
        if (status == null || status == AdminGroupStatus.ACTIVE) {
            return "g.is_active AND " + HEADMAN_EXISTS;
        }
        if (status == AdminGroupStatus.DRAFT) {
            return "g.is_active AND NOT (" + HEADMAN_EXISTS + ")";
        }
        return "NOT g.is_active";
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return "";
        return "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
    }

    private static GroupRegistryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupRegistryRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("alphabetic_code"),
                rs.getString("numeric_code"),
                (Integer) rs.getObject("current_course"),
                (Integer) rs.getObject("training_duration_years"),
                rs.getString("duration_status"),
                rs.getBoolean("is_active"),
                rs.getLong("student_count"),
                rs.getString("headman_fio"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    public record GroupRegistryRow(
            long id,
            String name,
            String alphabeticCode,
            String numericCode,
            Integer currentCourse,
            Integer trainingDurationYears,
            String durationStatus,
            boolean active,
            long studentCount,
            String headmanFio,
            OffsetDateTime createdAt) {}

    public record GroupRegistryCounts(long activeCount, long draftCount, long archivedCount) {}
}
