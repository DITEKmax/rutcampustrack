package ru.rutcampustrack.academic.group;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.academic.contract.api.GroupOptionsApi;
import ru.rutcampustrack.academic.contract.dto.group.GroupOptionsResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.security.RequireRole;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

@RestController
public class GroupOptionsController implements GroupOptionsApi {
    private final JdbcTemplate jdbc;

    public GroupOptionsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @RequireRole(
            value = {UserRole.ADMIN},
            admin = {"USERS_MANAGE", "GROUPS_VIEW"},
            adminAny = true)
    public GroupOptionsResponse options(String search, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw AdminAccessException.invalid();
        String pattern =
                "%"
                        + (search == null ? "" : search.trim())
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                        + "%";
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE archived_at IS NULL AND name ILIKE ? ESCAPE '!'",
                        Long.class,
                        pattern);
        var items =
                jdbc.query(
                        "SELECT id,name FROM groups WHERE archived_at IS NULL AND name ILIKE ? ESCAPE '!' ORDER BY name,id LIMIT ? OFFSET ?",
                        (rs, n) -> new GroupOptionsResponse.Item(rs.getLong(1), rs.getString(2)),
                        pattern,
                        size,
                        (long) page * size);
        return new GroupOptionsResponse(
                items, page, size, total, (int) ((total + size - 1) / size));
    }
}
