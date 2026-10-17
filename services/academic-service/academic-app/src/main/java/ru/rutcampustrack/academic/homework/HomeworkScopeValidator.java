package ru.rutcampustrack.academic.homework;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.shared.web.time.EducationalTime;

@Component
public class HomeworkScopeValidator {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public HomeworkScopeValidator(
            JdbcTemplate jdbc, Clock clock, ObjectProvider<EducationalTime> educationalTime) {
        this(jdbc, instructionalClock(clock, educationalTime));
    }

    private static Clock instructionalClock(
            Clock clock, ObjectProvider<EducationalTime> educationalTime) {
        EducationalTime instructionalTime = educationalTime.getIfAvailable();
        return instructionalTime == null ? clock : instructionalTime.clock();
    }

    public HomeworkScopeValidator(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void validate(long group, long subject, long semester, LocalDate date) {
        if (date == null
                || date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow"))))) {
            throw new BadRequestException("lessonDate", "Дата ДЗ должна быть не в прошлом");
        }
        Boolean subjectMatches =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM subjects WHERE id = ? AND group_id = ?)",
                        Boolean.class,
                        subject,
                        group);
        if (!Boolean.TRUE.equals(subjectMatches))
            throw new BadRequestException("subjectId", "Предмет не принадлежит группе");
        List<Boolean> semesterMatches =
                jdbc.query(
                        "SELECT ?::date BETWEEN date_from AND date_to FROM semesters WHERE id = ?",
                        (rs, index) -> rs.getBoolean(1),
                        date,
                        semester);
        if (semesterMatches.isEmpty() || !semesterMatches.getFirst()) {
            throw new BadRequestException(
                    "lessonDate", "Дата ДЗ должна находиться в указанном семестре");
        }
    }
}
