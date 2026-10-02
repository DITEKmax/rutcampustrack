package ru.rutcampustrack.academic.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantViewResponse;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read adapter for the same durable grant rows consumed by Auth. */
@Repository
public class UserRoleGrantReader {

    private static final String BASE_SQL = """
            SELECT g.user_id, g.role, g.status, g.group_id,
                   grp.name AS group_name, u.status::text AS user_status
            FROM user_role_grants g
            JOIN users u ON u.id=g.user_id
            LEFT JOIN groups grp ON grp.id = g.group_id
            WHERE g.user_id IN (%s)
            ORDER BY g.user_id, g.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public UserRoleGrantReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RoleGrantViewResponse> findByUserId(Long userId) {
        if (userId == null) return List.of();
        return findByUserIds(List.of(userId)).getOrDefault(userId, List.of());
    }

    public Map<Long, List<RoleGrantViewResponse>> findByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<Long> ids = userIds.stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        Map<Long, List<RoleGrantViewResponse>> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                BASE_SQL.formatted(placeholders),
                ps -> {
                    for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
                },
                (rs, rowNum) -> {
                    long userId = rs.getLong("user_id");
                    result.computeIfAbsent(userId, ignored -> new java.util.ArrayList<>())
                            .add(toView(rs));
                    return null;
                });
        return result;
    }

    private static RoleGrantViewResponse toView(ResultSet rs) throws SQLException {
        String role = rs.getString("role").toUpperCase(java.util.Locale.ROOT);
        String status = rs.getString("status").toUpperCase(java.util.Locale.ROOT);
        boolean selectable = "ACTIVE".equals(status);
        boolean freshAssignment = "ARCHIVED".equals(status) && !"archived".equals(rs.getString("user_status"))
                && List.of("STUDENT","TEACHER","ADMIN").contains(role);
        return new RoleGrantViewResponse(
                role,
                status,
                nullableLong(rs, "group_id"),
                rs.getString("group_name"),
                selectable,
                !selectable,
                applicableStatuses(role),
                freshAssignment || canUpdate(role, status),
                freshAssignment ? null : blockedReason(role, status));
    }

    private static List<String> applicableStatuses(String role) {
        return switch (role) {
            // ARCHIVED is a retained read-only lifecycle state for every
            // durable grant; it is filterable but never writable here.
            case "STUDENT" -> List.of("ACTIVE", "EXPELLED", "GRADUATED", "SUSPENDED", "ARCHIVED");
            case "TEACHER" -> List.of("ACTIVE", "DISMISSED", "SUSPENDED", "ARCHIVED");
            case "ADMIN" -> List.of("ACTIVE", "ARCHIVED");
            case "HEADMAN" -> List.of("ACTIVE", "SUSPENDED", "ARCHIVED");
            default -> List.of();
        };
    }

    private static boolean canUpdate(String role, String status) {
        return ("STUDENT".equals(role) || "TEACHER".equals(role))
                && !"ARCHIVED".equals(status);
    }

    private static String blockedReason(String role, String status) {
        if ("ARCHIVED".equals(status)) return "Архивная роль доступна только для чтения";
        if ("ADMIN".equals(role)) return "Статус ADMIN изменяется отдельным решением владельца";
        if ("HEADMAN".equals(role)) return "Роль старосты выводится из назначения студента";
        return null;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
