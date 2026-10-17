package ru.rutcampustrack.academic.adminattendance;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels.Semester;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Minimal report metadata, deliberately independent of user/group registry projections. */
@Repository
public class AdminAttendanceReadRepository {
    static final int MAX_ROWS = 10_000;
    private final JdbcTemplate jdbc;

    public AdminAttendanceReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Semester> semesters() {
        List<Semester> rows =
                jdbc.query(
                        "SELECT id,name,date_from,date_to,is_active,is_archived FROM semesters"
                                + " ORDER BY date_from DESC,id LIMIT 10001",
                        (rs, n) ->
                                new Semester(
                                        Long.toString(rs.getLong("id")),
                                        rs.getString("name"),
                                        rs.getDate("date_from").toLocalDate(),
                                        rs.getDate("date_to").toLocalDate(),
                                        rs.getBoolean("is_active"),
                                        rs.getBoolean("is_archived")));
        requireSize(rows.size());
        return List.copyOf(rows);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Selection select(long semesterId, String search) {
        List<Semester> semesters =
                jdbc.query(
                        "SELECT id,name,date_from,date_to,is_active,is_archived FROM semesters"
                                + " WHERE id=?",
                        (rs, n) ->
                                new Semester(
                                        Long.toString(rs.getLong("id")),
                                        rs.getString("name"),
                                        rs.getDate("date_from").toLocalDate(),
                                        rs.getDate("date_to").toLocalDate(),
                                        rs.getBoolean("is_active"),
                                        rs.getBoolean("is_archived")),
                        semesterId);
        if (semesters.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Семестр не найден");
        String pattern =
                "%"
                        + search.toLowerCase(Locale.ROOT)
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_")
                        + "%";
        List<Map.Entry<Long, String>> rows =
                jdbc.query(
                        "SELECT id,name FROM groups WHERE lower(name) LIKE ? ESCAPE '!' ORDER BY id"
                                + " LIMIT 10001",
                        (rs, n) -> Map.entry(rs.getLong("id"), rs.getString("name")),
                        pattern);
        requireSize(rows.size());
        Map<Long, String> names = new LinkedHashMap<>();
        for (var row : rows) {
            if (row.getKey() <= 0
                    || row.getValue().isBlank()
                    || names.put(row.getKey(), row.getValue()) != null)
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Метаданные групп недоступны");
        }
        return new Selection(semesters.getFirst(), Map.copyOf(names));
    }

    private static void requireSize(int size) {
        if (size > MAX_ROWS)
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "Сузь поиск: слишком много групп");
    }

    public record Selection(Semester semester, Map<Long, String> groups) {}
}
