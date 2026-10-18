package ru.rutcampustrack.academic.repository;

import io.grpc.Status;

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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Academic-owned read model for the ADMIN registry, dashboard and promotion preview.
 *
 * <p>Local eligibility describes group membership, never live Auth admission. Every query reads
 * pending-operation guards and its result in one PostgreSQL statement snapshot, including callers
 * using READ_COMMITTED. Registry items and their total share that same snapshot.
 */
@Repository
public class GroupRegistryReadRepository {

    // Same unscoped publication guard as AcademicIdentityReadFence.requireRegistryReadable().
    // Availability guard/headman rows always deny, regardless of the operation's phase.
    private static final String REGISTRY_GUARD =
            """
            SELECT EXISTS(SELECT 1 FROM academic_identity_operation_targets t
                          JOIN academic_identity_operations o ON o.operation_id=t.operation_id
                          WHERE t.fenced AND o.phase<>'SUCCEEDED')
                OR EXISTS(SELECT 1 FROM academic_identity_operation_groups g
                          JOIN academic_identity_operations o ON o.operation_id=g.operation_id
                          WHERE g.fenced AND o.phase<>'SUCCEEDED')
                OR EXISTS(SELECT 1 FROM academic_group_availability_guards)
                OR EXISTS(SELECT 1 FROM academic_group_availability_headmen) AS blocked
            """;

    private static final String GROUPS_GUARD =
            """
            SELECT EXISTS(SELECT 1 FROM academic_identity_operation_groups g
                          JOIN academic_identity_operations o ON o.operation_id=g.operation_id
                          JOIN selected_groups scope ON scope.id=g.group_id
                          WHERE g.fenced AND o.phase<>'SUCCEEDED')
                OR EXISTS(SELECT 1 FROM academic_identity_operation_targets t
                          JOIN academic_identity_operations o ON o.operation_id=t.operation_id
                          JOIN academic_role_eligibility e ON e.user_id=t.user_id
                          JOIN selected_groups scope ON scope.id=e.group_id
                          WHERE t.fenced AND o.phase<>'SUCCEEDED')
                OR EXISTS(SELECT 1 FROM academic_group_availability_guards g
                          JOIN selected_groups scope ON scope.id=g.group_id)
                OR EXISTS(SELECT 1 FROM academic_group_availability_headmen h
                          JOIN academic_role_eligibility e ON e.user_id=h.user_id
                          JOIN selected_groups scope ON scope.id=e.group_id) AS blocked
            """;

    // No runtime inference from display names: only a consistent persisted tuple
    // is authoritative. The same projection feeds items, filters and ordering.
    private static final String REGISTRY =
            """
            WITH read_guard AS (
            """
                    + REGISTRY_GUARD
                    + """
                    ), metadata AS (
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
            EXISTS (SELECT 1 FROM academic_role_eligibility hg
                    JOIN academic_profiles hu ON hu.id = hg.user_id
                    WHERE hg.group_id = g.id AND hg.role = 'HEADMAN'
                      AND hg.status = 'ACTIVE' AND hu.archived_at IS NULL)
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
SELECT row_number() OVER (ORDER BY %s) AS page_order, g.id, g.name,
       CASE WHEN g.known_code THEN g.alphabetic_code END AS alphabetic_code,
       CASE WHEN g.known_code THEN g.numeric_code END AS numeric_code,
       g.registry_course AS current_course, g.program_type_digit,
       CASE WHEN g.known_code THEN g.training_duration_years END AS training_duration_years,
       CASE WHEN g.known_code THEN 'KNOWN' ELSE 'LEGACY_UNKNOWN' END AS duration_status,
       g.is_active, g.created_at,
       (SELECT count(*) FROM academic_role_eligibility sg
        JOIN academic_profiles su ON su.id = sg.user_id
        WHERE sg.group_id = g.id AND sg.role = 'STUDENT'
          AND sg.status = 'ACTIVE' AND su.archived_at IS NULL) AS student_count,
       (SELECT nullif(trim(concat_ws(' ', hu.last_name, hu.first_name, hu.middle_name)), '')
        FROM academic_role_eligibility hg
        JOIN academic_profiles hu ON hu.id = hg.user_id
        WHERE hg.group_id = g.id AND hg.role = 'HEADMAN'
          AND hg.status = 'ACTIVE' AND hu.archived_at IS NULL
        ORDER BY hu.last_name, hu.first_name, hu.id
        LIMIT 1) AS headman_fio
FROM filtered g
ORDER BY %s
LIMIT ? OFFSET ?
""";

    private static final String COUNTS =
            """
            SELECT (SELECT blocked FROM read_guard) AS blocked,
                   count(*) FILTER (WHERE g.is_active AND %s) AS active_count,
                   count(*) FILTER (WHERE g.is_active AND NOT (%s)) AS draft_count,
                   count(*) FILTER (WHERE NOT g.is_active) AS archived_count
            FROM registry g
            WHERE NOT (SELECT blocked FROM read_guard) AND %s
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
        String ordering = orderBy(pageable.getSort());
        String query =
                REGISTRY
                        + ", filtered AS (SELECT g.* FROM registry g "
                        + "WHERE NOT (SELECT blocked FROM read_guard) AND %s AND %s), "
                                .formatted(statusPredicate(status), FILTERS)
                        + "totals AS (SELECT count(*) AS total_count FROM filtered), page AS ("
                        + PAGE_SELECT.formatted(ordering, ordering)
                        + ") SELECT guard.blocked, totals.total_count, page.* "
                        + "FROM read_guard guard CROSS JOIN totals LEFT JOIN page ON TRUE "
                        + "ORDER BY page.page_order";
        List<Object> args = filterArgs(search, course, studyMode, educationLevel);
        args.add(pageable.getPageSize());
        args.add(pageable.getOffset());
        return jdbcTemplate.query(
                query,
                rs -> {
                    if (!rs.next()) throw unavailable();
                    requireReadable(rs);
                    long total = rs.getLong("total_count");
                    List<GroupRegistryRow> rows = new ArrayList<>();
                    do {
                        // The total/guard row survives an empty or out-of-range page.
                        if (rs.getObject("id") != null) rows.add(mapRow(rs, rows.size()));
                    } while (rs.next());
                    return new PageImpl<>(rows, pageable, total);
                },
                args.toArray());
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
                (rs, rowNum) -> {
                    requireReadable(rs);
                    return new GroupRegistryCounts(
                            rs.getLong("active_count"),
                            rs.getLong("draft_count"),
                            rs.getLong("archived_count"));
                });
    }

    /** Counts the same current student population exposed by the ADMIN registry. */
    public Map<Long, Long> countActiveStudentsByGroupIds(Collection<Long> groupIds) {
        List<Long> ids = groupIds.stream().distinct().sorted().toList();
        if (ids.isEmpty()) return Map.of();

        String selected = String.join(",", ids.stream().map(ignored -> "(?::bigint)").toList());
        String query =
                "WITH selected_groups(id) AS (VALUES "
                        + selected
                        + "), read_guard AS ("
                        + GROUPS_GUARD
                        + """
                        ), counts AS (
                            SELECT sg.group_id, count(*) AS student_count
                            FROM academic_role_eligibility sg
                            JOIN academic_profiles su ON su.id=sg.user_id
                            JOIN selected_groups scope ON scope.id=sg.group_id
                            WHERE NOT (SELECT blocked FROM read_guard)
                              AND sg.role='STUDENT' AND sg.status='ACTIVE'
                              AND su.archived_at IS NULL
                            GROUP BY sg.group_id
                        )
                        SELECT guard.blocked, counts.group_id, counts.student_count
                        FROM read_guard guard LEFT JOIN counts ON TRUE
                        """;
        return jdbcTemplate.query(
                query,
                rs -> {
                    if (!rs.next()) throw unavailable();
                    requireReadable(rs);
                    Map<Long, Long> result = new HashMap<>();
                    do {
                        if (rs.getObject("group_id") != null) {
                            result.put(rs.getLong("group_id"), rs.getLong("student_count"));
                        }
                    } while (rs.next());
                    return result;
                },
                ids.toArray());
    }

    private static void requireReadable(ResultSet rs) throws SQLException {
        if (rs.getBoolean("blocked") || rs.wasNull()) throw unavailable();
    }

    private static io.grpc.StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("group identity operation publication is pending")
                .asRuntimeException();
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
