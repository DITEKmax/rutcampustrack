package ru.rutcampustrack.academic.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.academic.contract.dto.group.AdminGroupStatus;
import ru.rutcampustrack.academic.contract.dto.group.EducationLevel;
import ru.rutcampustrack.academic.contract.dto.group.StudyMode;
import ru.rutcampustrack.academic.exception.BadRequestException;

/**
 * Read model for the ADMIN registry. Status, student count and headman are resolved in one
 * parameterized SQL projection so the page does not perform per-row follow-up requests.
 */
@Repository
public class GroupRegistryReadRepository {

    // No runtime inference from display names: only a consistent persisted tuple
    // is authoritative. The same projection feeds items, filters and ordering.
    private static final String REGISTRY =
            """
            WITH metadata AS (
                SELECT raw.*,
                       (duration_status = 'KNOWN'
                        AND alphabetic_code ~ '^[А-ЯЁ][А-ЯЁа-яё]{1,3}$'
                        AND numeric_code ~ '^[1-9][1-9][0-9]$'
                        AND current_course::text = substring(numeric_code, 1, 1)
                        AND training_duration_years >= current_course) AS known_code
                FROM groups raw
            ), registry AS (
                SELECT metadata.*,
                       CASE WHEN known_code THEN current_course END AS registry_course,
                       CASE WHEN known_code THEN substring(numeric_code, 2, 1)::integer END
                           AS program_type_digit
                FROM metadata
            )
            """;

    private static final String HEADMAN_EXISTS =
            """
            EXISTS (SELECT 1 FROM user_role_grants hg
                    JOIN users hu ON hu.id = hg.user_id
                    WHERE hg.group_id = g.id AND hg.role = 'headman'
                      AND hg.status = 'active' AND hu.status <> 'archived')
            """;

    private static final String SEARCH =
            """
            (? = '' OR lower(g.name) LIKE ?
             OR lower(coalesce(g.alphabetic_code, '')) LIKE ?
             OR lower(coalesce(g.numeric_code, '')) LIKE ?)
            """;

    private static final String FILTERS =
            SEARCH
                    + """
            AND (?::integer IS NULL OR g.registry_course = ?)
            AND (?::varchar IS NULL OR
                 CASE (g.program_type_digit - 1) % 3
                     WHEN 0 THEN 'FULL_TIME' WHEN 1 THEN 'PART_TIME'
                     WHEN 2 THEN 'EXTRAMURAL' END = ?)
            AND (?::varchar IS NULL OR
                 CASE WHEN g.program_type_digit <= 3 THEN 'BACHELOR'
                      WHEN g.program_type_digit <= 6 THEN 'SPECIALIST'
                      WHEN g.program_type_digit <= 9 THEN 'MASTER' END = ?)
            """;

    private static final String PAGE_SELECT =
            """
            SELECT g.id, g.name,
                   CASE WHEN g.known_code THEN g.alphabetic_code END AS alphabetic_code,
                   CASE WHEN g.known_code THEN g.numeric_code END AS numeric_code,
                   g.registry_course AS current_course, g.program_type_digit,
                   CASE WHEN g.known_code THEN g.training_duration_years END AS training_duration_years,
                   CASE WHEN g.known_code THEN 'KNOWN' ELSE 'LEGACY_UNKNOWN' END AS duration_status,
                   g.is_active, g.created_at,
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
            FROM registry g
            WHERE %s AND %s
            ORDER BY %s
            LIMIT ? OFFSET ?
            """;

    private static final String COUNTS =
            """
            SELECT count(*) FILTER (WHERE g.is_active AND %s) AS active_count,
                   count(*) FILTER (WHERE g.is_active AND NOT (%s)) AS draft_count,
                   count(*) FILTER (WHERE NOT g.is_active) AS archived_count
            FROM registry g
            WHERE %s
            """;

    private final JdbcTemplate jdbcTemplate;

    public GroupRegistryReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<GroupRegistryRow> find(AdminGroupStatus status, String search, Pageable pageable) {
        return find(status, search, null, null, null, pageable);
    }

    public Page<GroupRegistryRow> find(
            AdminGroupStatus status,
            String search,
            Integer course,
            StudyMode studyMode,
            EducationLevel educationLevel,
            Pageable pageable) {
        String query =
                REGISTRY
                        + PAGE_SELECT.formatted(
                                statusPredicate(status), FILTERS, orderBy(pageable.getSort()));
        List<Object> args = filterArgs(search, course, studyMode, educationLevel);
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        List<GroupRegistryRow> rows =
                jdbcTemplate.query(query, GroupRegistryReadRepository::mapRow, args.toArray());
        long total = count(status, search, course, studyMode, educationLevel);
        return new PageImpl<>(rows, pageable, total);
    }

    public GroupRegistryCounts countAll(String search) {
        return countAll(search, null, null, null);
    }

    public GroupRegistryCounts countAll(
            String search, Integer course, StudyMode studyMode, EducationLevel educationLevel) {
        String query = REGISTRY + COUNTS.formatted(HEADMAN_EXISTS, HEADMAN_EXISTS, FILTERS);
        return jdbcTemplate.queryForObject(
                query,
                filterArgs(search, course, studyMode, educationLevel).toArray(),
                (rs, rowNum) ->
                        new GroupRegistryCounts(
                                rs.getLong("active_count"),
                                rs.getLong("draft_count"),
                                rs.getLong("archived_count")));
    }

    /** Counts the same current student population exposed by the ADMIN registry. */
    public Map<Long, Long> countActiveStudentsByGroupIds(Collection<Long> groupIds) {
        List<Long> ids = groupIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String query =
                """
                SELECT sg.group_id, count(*) AS student_count
                FROM user_role_grants sg
                JOIN users su ON su.id = sg.user_id
                WHERE sg.role = 'student' AND sg.status = 'active'
                  AND su.status <> 'archived'
                  AND sg.group_id IN (%s)
                GROUP BY sg.group_id
                """
                        .formatted(placeholders);
        Map<Long, Long> result = new HashMap<>();
        jdbcTemplate.query(
                query,
                statement -> {
                    for (int i = 0; i < ids.size(); i++) statement.setLong(i + 1, ids.get(i));
                },
                rs -> {
                    result.put(rs.getLong("group_id"), rs.getLong("student_count"));
                });
        return result;
    }

    private long count(
            AdminGroupStatus status,
            String search,
            Integer course,
            StudyMode studyMode,
            EducationLevel educationLevel) {
        String query =
                REGISTRY
                        + "SELECT count(*) FROM registry g WHERE %s AND %s"
                                .formatted(statusPredicate(status), FILTERS);
        Long value =
                jdbcTemplate.queryForObject(
                        query,
                        filterArgs(search, course, studyMode, educationLevel).toArray(),
                        Long.class);
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

    private static List<Object> filterArgs(
            String search, Integer course, StudyMode studyMode, EducationLevel educationLevel) {
        String wildcard = normalizeSearch(search);
        List<Object> args = new ArrayList<>();
        Collections.addAll(
                args,
                wildcard,
                wildcard,
                wildcard,
                wildcard,
                course,
                course,
                studyMode == null ? null : studyMode.name(),
                studyMode == null ? null : studyMode.name(),
                educationLevel == null ? null : educationLevel.name(),
                educationLevel == null ? null : educationLevel.name());
        return args;
    }

    private static String orderBy(Sort sort) {
        if (sort.isUnsorted()) {
            return "coalesce(g.alphabetic_code, g.name), coalesce(g.numeric_code, ''), g.id ASC";
        }
        List<String> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column =
                    switch (order.getProperty()) {
                        case "name" -> "g.name";
                        case "currentCourse" -> "g.registry_course";
                        case "programType" -> "g.program_type_digit";
                        default ->
                                throw new BadRequestException(
                                        "sort", "Недопустимое поле сортировки");
                    };
            orders.add(column + (order.isAscending() ? " ASC" : " DESC") + " NULLS LAST");
        }
        orders.add("g.id ASC");
        return String.join(", ", orders);
    }

    private static GroupRegistryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new GroupRegistryRow(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("alphabetic_code"),
                rs.getString("numeric_code"),
                (Integer) rs.getObject("current_course"),
                (Integer) rs.getObject("program_type_digit"),
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
            Integer programTypeDigit,
            Integer trainingDurationYears,
            String durationStatus,
            boolean active,
            long studentCount,
            String headmanFio,
            OffsetDateTime createdAt) {}

    public record GroupRegistryCounts(long activeCount, long draftCount, long archivedCount) {}
}
