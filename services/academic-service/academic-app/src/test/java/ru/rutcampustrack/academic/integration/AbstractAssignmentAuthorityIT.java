package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Real database fixtures shared by the L5A assignment authority integration
 * tests.  Producers still use the REST entrypoints; SQL here creates isolated
 * users, grants, groups, semesters, and canonical subject type rows only.
 */
public abstract class AbstractAssignmentAuthorityIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static final String CURRENT_SEMESTER_NAME =
            "L5A current authority semester " + LocalDate.now(MOSCOW);

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ObjectMapper objectMapper;

    protected Fixture newFixture(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        // V8 caps groups.name at 32 characters; keep enough UUID material to
        // make concurrently-created fixture groups distinct without using the
        // caller label in the persisted name.
        long groupId = insertGroup("L5A-" + suffix.substring(0, 12) + "-" + suffix.substring(12, 24));
        long headmanId = insertUser("l5a-h-" + suffix.substring(0, 20), "Headman", "Student",
                "student", true, groupId, null);
        String employee1 = "L5A-" + suffix.substring(0, 12) + "-1";
        String employee2 = "L5A-" + suffix.substring(0, 12) + "-2";
        String employeeWithoutGrant = "L5A-" + suffix.substring(0, 12) + "-3";
        long teacher1 = insertUser("l5a-t1-" + suffix.substring(0, 18), "Teacher", "One",
                "teacher", false, null, employee1);
        long teacher2 = insertUser("l5a-t2-" + suffix.substring(0, 18), "Teacher", "Two",
                "teacher", false, null, employee2);
        long teacherWithoutGrant = insertUser("l5a-t3-" + suffix.substring(0, 18), "Teacher", "Three",
                "teacher", false, null, employeeWithoutGrant);
        insertGrant(teacher1, "active");
        insertGrant(teacher2, "active");

        LocalDate dateFrom = allocateSemesterFrom();
        long semesterId = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                Long.class,
                "L5A " + label + " semester " + suffix,
                dateFrom, dateFrom.plusDays(29));
        LocalDate dateTo = dateFrom.plusDays(29);
        return new Fixture(groupId, headmanId, teacher1, teacher2, teacherWithoutGrant,
                employee1, employee2, employeeWithoutGrant, semesterId, dateFrom, dateTo);
    }

    protected long insertSubject(long groupId, String name, String... lessonTypes) {
        if (lessonTypes.length == 0 || lessonTypes.length > 3) {
            throw new IllegalArgumentException("fixture subject needs one to three lesson types");
        }
        StringBuilder sql = new StringBuilder(
                "WITH inserted_subject AS ("
                        + "INSERT INTO subjects (name, type, group_id) VALUES (?, ?::subject_type, ?) RETURNING id"
                        + "), inserted_lesson_types AS ("
                        + "INSERT INTO subject_lesson_types (subject_id, lesson_type) ");
        for (int index = 0; index < lessonTypes.length; index++) {
            if (index > 0) {
                sql.append(" UNION ALL ");
            }
            sql.append("SELECT id, ?::subject_type FROM inserted_subject");
        }
        sql.append(" RETURNING subject_id) SELECT subject_id FROM inserted_lesson_types LIMIT 1");
        Object[] arguments = new Object[3 + lessonTypes.length];
        arguments[0] = name;
        arguments[1] = lessonTypes[0].toLowerCase();
        arguments[2] = groupId;
        for (int index = 0; index < lessonTypes.length; index++) {
            arguments[3 + index] = lessonTypes[index].toLowerCase();
        }
        return jdbcTemplate.queryForObject(sql.toString(), Long.class, arguments);
    }

    protected long ensureCurrentActiveSemester() {
        List<Long> existing = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE name = ? ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), CURRENT_SEMESTER_NAME);
        long semesterId;
        if (existing.isEmpty()) {
            LocalDate from = LocalDate.now(MOSCOW).minusDays(5);
            LocalDate to = LocalDate.now(MOSCOW).plusDays(30);
            semesterId = jdbcTemplate.queryForObject(
                    "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                            + "VALUES (?, ?, ?, false, NOW()) RETURNING id",
                    Long.class, CURRENT_SEMESTER_NAME, from, to);
        } else {
            semesterId = existing.get(0);
        }
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
        return semesterId;
    }

    protected List<Long> activeSemesterIds() {
        return jdbcTemplate.query("SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
    }

    protected void restoreActiveSemesterIds(List<Long> ids) {
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", id);
        }
    }

    protected long assignmentCount(long subjectId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM assignments WHERE subject_id = ?", Integer.class, subjectId);
        return count == null ? 0L : count;
    }

    protected long insertGroup(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO groups (name, is_active) VALUES (?, true) RETURNING id",
                Long.class, name);
    }

    private long insertUser(String login,
                            String lastName,
                            String firstName,
                            String role,
                            boolean headman,
                            Long groupId,
                            String employeeNumber) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                        + "is_headman, group_id, employee_number, password_changed, created_at, updated_at) "
                        + "VALUES (?, NULL, ?, ?, ?::user_role, 'active'::account_status, ?, ?, ?, false, NOW(), NOW()) "
                        + "RETURNING id",
                Long.class, login, lastName, firstName, role, headman, groupId, employeeNumber);
    }

    private void insertGrant(long userId, String status) {
        jdbcTemplate.update(
                "INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'teacher', ?, NULL, NOW(), NOW())",
                userId, status);
    }

    private LocalDate allocateSemesterFrom() {
        LocalDate candidate = LocalDate.of(2200, 1, 1);
        while (Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM semesters WHERE date_from <= ? AND date_to >= ?)",
                Boolean.class, candidate.plusDays(29), candidate))) {
            candidate = candidate.plusDays(31);
        }
        return candidate;
    }

    protected record Fixture(long groupId,
                             long headmanId,
                             long teacher1Id,
                             long teacher2Id,
                             long teacherWithoutGrantId,
                             String employee1,
                             String employee2,
                             String employeeWithoutGrant,
                             long semesterId,
                             LocalDate dateFrom,
                             LocalDate dateTo) {
    }

}
