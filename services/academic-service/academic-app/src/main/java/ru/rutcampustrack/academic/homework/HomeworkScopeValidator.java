package ru.rutcampustrack.academic.homework;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.BadRequestException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class HomeworkScopeValidator {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public HomeworkScopeValidator(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }
    public void validate(long group, long subject, long semester, LocalDate date) {
        if (date == null || date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow"))))) {
            throw new BadRequestException("lessonDate", "Дата ДЗ должна быть не в прошлом");
        }
        Boolean subjectMatches = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM subjects WHERE id = ? AND group_id = ?)",
                Boolean.class, subject, group);
        if (!Boolean.TRUE.equals(subjectMatches)) throw new BadRequestException("subjectId", "Предмет не принадлежит группе");
        List<Boolean> semesterMatches = jdbc.query("SELECT ?::date BETWEEN date_from AND date_to FROM semesters WHERE id = ?",
                (rs, index) -> rs.getBoolean(1), date, semester);
        if (semesterMatches.isEmpty() || !semesterMatches.getFirst()) {
            throw new BadRequestException("lessonDate", "Дата ДЗ должна находиться в указанном семестре");
        }
    }
}
