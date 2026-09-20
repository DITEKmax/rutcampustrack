package ru.rutcampustrack.schedule.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostgreSQL behavior checks for the B0 schedule V1-V17 chain. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class StudentOccurrenceMigrationIT {

    private static final String PHYSICAL_SNAPSHOT_MISMATCH =
            "physical lesson identity snapshot does not match its occurrence";
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_student_b0_schedule")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    private DataSource adminDataSource;
    private JdbcTemplate adminJdbc;
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        POSTGRES.start();
        adminDataSource = dataSourceForSchema(null);
        adminJdbc = new JdbcTemplate(adminDataSource);
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void freshFullChainMigratesValidatesAndRepeatsWithoutWork() {
        String schema = newSchema();
        Flyway flyway = flyway(schema, null);

        MigrateResult first = flyway.migrate();

        assertThat(first.success).isTrue();
        assertThat(first.migrationsExecuted).isPositive();
        assertThat(flyway.info().pending()).isEmpty();
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion().getVersion()).isEqualTo("17");
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void assignmentAndGenerationIdentityRetainPhysicalHistory() {
        String schema = newSchema();
        flyway(schema, "17").migrate();
        Long templateId = insertTemplate(schema, 700L, 10L, 20L, 30L, 2, "09:00", "10:00");
        Long occurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-02', 700, 10, 20, 30, 40, 'lecture') RETURNING id",
                Long.class, templateId);
        Long sourceLessonId = insertLesson(schema, templateId, occurrenceId, 700, 10, 20, 30, 40,
                "lecture", "2026-03-02", "cancelled", 2, "09:00", "10:00", 1, 1);
        Long targetLessonId = insertLesson(schema, templateId, occurrenceId, 700, 10, 20, 30, 40,
                "lecture", "2026-03-02", "planned", 2, "09:00", "10:00", 2, 2);
        jdbc.update("UPDATE " + table(schema, "lesson_occurrences")
                + " SET generation = 2, revision = 2, current_lesson_id = ? WHERE id = ?",
                targetLessonId, occurrenceId);

        // A template edit affects future planning only. Retained physical
        // snapshots may still change lifecycle state without being rewritten.
        jdbc.update("UPDATE " + table(schema, "schedule_items")
                + " SET start_time = '08:00', end_time = '09:00' WHERE id = ?", templateId);
        jdbc.update("UPDATE " + table(schema, "lessons")
                + " SET status = 'active', revision = 3 WHERE id = ?", targetLessonId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "lessons")
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_lesson_id FROM "
                        + table(schema, "lesson_occurrences") + " WHERE id = ?", Long.class, occurrenceId))
                .isEqualTo(targetLessonId);

        UUID pendingKey = UUID.randomUUID();
        jdbc.update("INSERT INTO " + table(schema, "lesson_homework_bindings")
                        + " (occurrence_id, current_lesson_id, actor_id, request_key, payload_hash, state)"
                        + " VALUES (?, ?, 2, ?, decode(repeat('11', 32), 'hex'), 'PENDING')",
                occurrenceId, targetLessonId, pendingKey);
        jdbc.update("INSERT INTO " + table(schema, "lesson_homework_bindings")
                        + " (occurrence_id, current_lesson_id, homework_id, actor_id, request_key,"
                        + " payload_hash, state) VALUES (?, ?, 501, 3, ?, decode(repeat('22', 32), 'hex'), 'ACTIVE')",
                occurrenceId, targetLessonId, UUID.randomUUID());
        Long archivedBindingId = jdbc.queryForObject("INSERT INTO "
                        + table(schema, "lesson_homework_bindings")
                        + " (occurrence_id, current_lesson_id, actor_id, request_key, payload_hash, state)"
                        + " VALUES (?, ?, 4, ?, decode(repeat('33', 32), 'hex'), 'ARCHIVED') RETURNING binding_id",
                Long.class, occurrenceId, targetLessonId, UUID.randomUUID());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "
                        + table(schema, "lesson_homework_bindings") + " WHERE occurrence_id = ?",
                Integer.class, occurrenceId)).isEqualTo(3);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO "
                        + table(schema, "lesson_homework_bindings")
                        + " (occurrence_id, current_lesson_id, homework_id, actor_id, request_key,"
                        + " payload_hash, state) VALUES (?, ?, 501, 5, ?, decode(repeat('44', 32), 'hex'), 'ACTIVE')",
                occurrenceId, targetLessonId, UUID.randomUUID())).isInstanceOf(DataAccessException.class);

        Long pendingBindingId = jdbc.queryForObject("SELECT binding_id FROM "
                + table(schema, "lesson_homework_bindings")
                + " WHERE request_key = ?", Long.class, pendingKey);
        jdbc.update("UPDATE " + table(schema, "lesson_homework_bindings")
                + " SET homework_id = 502, state = 'ACTIVE', revision = 2 WHERE binding_id = ?",
                pendingBindingId);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "lesson_homework_bindings")
                        + " SET homework_id = 503 WHERE binding_id = ?", pendingBindingId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM "
                        + table(schema, "lesson_homework_bindings") + " WHERE binding_id = ?", archivedBindingId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "lessons")
                        + " SET room_snapshot = 'B202' WHERE id = ?", sourceLessonId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table(schema, "lessons")
                        + " WHERE id = ?", sourceLessonId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void lineageReplayAndOwnershipConstraintsRejectForeignOrMutableRows() {
        String schema = newSchema();
        flyway(schema, "17").migrate();
        Long templateId = insertTemplate(schema, 701L, 11L, 21L, 31L, 3, "11:00", "12:00");
        Long occurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-05', 701, 11, 21, 31, 41, 'practice') RETURNING id",
                Long.class, templateId);
        Long lessonId = insertLesson(schema, templateId, occurrenceId, 701, 11, 21, 31, 41,
                "practice", "2026-03-05", "planned", 3, "11:00", "12:00", 1, 1);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-05', 702, 11, 21, 31, 41, 'practice')", templateId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "lesson_occurrences")
                        + " SET assignment_id = 999 WHERE id = ?", occurrenceId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> insertLesson(schema, templateId, occurrenceId, 701, 999, 21, 31, 41,
                        "practice", "2026-03-05", "planned", 3, "11:00", "12:00", 3, 3))
                .isInstanceOf(DataAccessException.class);

        jdbc.update("INSERT INTO " + table(schema, "lesson_lifecycle_entries")
                        + " (occurrence_id, revision, action, lesson_id, generation, actor_id, occurred_at)"
                        + " VALUES (?, 1, 'CREATED', ?, 1, 2, NOW())", occurrenceId, lessonId);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "lesson_lifecycle_entries")
                        + " SET reason = 'changed' WHERE occurrence_id = ?", occurrenceId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table(schema, "lesson_lifecycle_entries")
                        + " WHERE occurrence_id = ?", occurrenceId)).isInstanceOf(DataAccessException.class);

        Long targetId = insertLesson(schema, templateId, occurrenceId, 701, 11, 21, 31, 41,
                "practice", "2026-03-05", "cancelled", 3, "11:00", "12:00", 2, 2);
        UUID replayKey = UUID.randomUUID();
        jdbc.update("INSERT INTO " + table(schema, "schedule_transfer_replay")
                        + " (actor_id, request_key, occurrence_id, payload_hash, source_lesson_id,"
                        + " target_lesson_id, accepted_revision) VALUES (2, ?, ?, decode(repeat('55', 32), 'hex'), ?, ?, 2)",
                replayKey, occurrenceId, lessonId, targetId);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "schedule_transfer_replay")
                        + " SET accepted_revision = 3 WHERE request_key = ?", replayKey))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table(schema, "schedule_transfer_replay")
                        + " WHERE request_key = ?", replayKey)).isInstanceOf(DataAccessException.class);

        Long otherTemplateId = insertTemplate(schema, 703L, 12L, 22L, 32L, 4, "13:00", "14:00");
        Long otherOccurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-06', 703, 12, 22, 32, 42, 'lab') RETURNING id",
                Long.class, otherTemplateId);
        Long otherLessonId = insertLesson(schema, otherTemplateId, otherOccurrenceId, 703, 12, 22, 32, 42,
                "lab", "2026-03-06", "planned", 4, "13:00", "14:00", 1, 1);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "lesson_occurrences")
                        + " SET current_lesson_id = ? WHERE id = ?", otherLessonId, occurrenceId))
                .isInstanceOf(DataAccessException.class);
        assertThat(otherOccurrenceId).isPositive();
    }

    @Test
    void crossTemplateRecurringOriginIsRejectedEvenWhenSnapshotsMatch() {
        String schema = newSchema();
        flyway(schema, "17").migrate();
        Long templateA = insertTemplate(schema, 704L, 13L, 23L, 33L, 5, "15:00", "16:00");
        Long templateB = jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_items")
                        + " (assignment_id, group_id, subject_id, semester_id, day_of_week, lesson_number,"
                        + " start_time, end_time, week_type, room) VALUES (704, 13, 23, 33, 1, 5,"
                        + " '15:00'::time, '16:00'::time, 'even'::week_type, 'A101') RETURNING id",
                Long.class);
        Long occurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (schedule_item_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-16', 704, 13, 23, 33, 43, 'lecture') RETURNING id",
                Long.class, templateA);
        Long validLessonId = insertLesson(schema, templateA, occurrenceId, 704, 13, 23, 33, 43,
                "lecture", "2026-03-16", "planned", 5, "15:00", "16:00", 1, 1);
        String lessons = table(schema, "lessons");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT schedule_item_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(templateA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");

        assertExactSqlFailure("P0001", PHYSICAL_SNAPSHOT_MISMATCH,
                () -> insertLesson(schema, templateB, occurrenceId, 704, 13, 23, 33, 43,
                        "lecture", "2026-03-16", "cancelled", 5, "15:00", "16:00", 2, 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT schedule_item_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(templateA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");

        assertExactSqlFailure("P0001", PHYSICAL_SNAPSHOT_MISMATCH,
                () -> jdbc.update("UPDATE " + lessons
                        + " SET schedule_item_id = ? WHERE id = ?", templateB, validLessonId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT schedule_item_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(templateA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");
    }

    @Test
    void oneOffOriginRetainsIdentityWhileRestoredGenerationMovesSlot() {
        String schema = newSchema();
        flyway(schema, "17").migrate();
        Long oneOffId = jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_one_off_lessons")
                        + " (group_id, subject_id, semester_id, date, lesson_number, classroom, created_by)"
                        + " VALUES (50, 60, 70, DATE '2026-03-10', 2, 'C101', 2) RETURNING id",
                Long.class);
        Long occurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (one_off_lesson_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-10', 800, 50, 60, 70, 80, 'lecture') RETURNING id",
                Long.class, oneOffId);
        Long sourceLessonId = insertOneOffLesson(schema, oneOffId, occurrenceId, 800, 50, 60, 70, 80,
                "lecture", "2026-03-10", "cancelled", 2, "09:00", "10:00", 1, 1);
        Long targetLessonId = insertOneOffLesson(schema, oneOffId, occurrenceId, 800, 50, 60, 70, 80,
                "lecture", "2026-03-11", "planned", 4, "11:00", "12:00", 2, 2);
        jdbc.update("UPDATE " + table(schema, "lesson_occurrences")
                + " SET generation = 2, revision = 2, current_lesson_id = ? WHERE id = ?",
                targetLessonId, occurrenceId);
        jdbc.update("UPDATE " + table(schema, "schedule_one_off_lessons")
                + " SET physical_lesson_id = ? WHERE id = ?", targetLessonId, oneOffId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "lessons")
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_lesson_id FROM "
                        + table(schema, "lesson_occurrences") + " WHERE id = ?", Long.class, occurrenceId))
                .isEqualTo(targetLessonId);
        assertThat(sourceLessonId).isPositive();
    }

    @Test
    void crossOneOffOriginIsRejectedEvenWhenSnapshotsMatch() {
        String schema = newSchema();
        flyway(schema, "17").migrate();
        Long oneOffA = jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_one_off_lessons")
                        + " (group_id, subject_id, semester_id, date, lesson_number, classroom, created_by)"
                        + " VALUES (51, 61, 71, DATE '2026-03-20', 3, 'C101', 2) RETURNING id",
                Long.class);
        Long oneOffB = jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_one_off_lessons")
                        + " (group_id, subject_id, semester_id, date, lesson_number, classroom, created_by)"
                        + " VALUES (51, 61, 71, DATE '2026-03-21', 3, 'C102', 2) RETURNING id",
                Long.class);
        Long occurrenceId = jdbc.queryForObject("INSERT INTO " + table(schema, "lesson_occurrences")
                        + " (one_off_lesson_id, occurrence_date, assignment_id, group_id, subject_id,"
                        + " semester_id, assigned_teacher_id, lesson_type)"
                        + " VALUES (?, DATE '2026-03-20', 805, 51, 61, 71, 81, 'practice') RETURNING id",
                Long.class, oneOffA);
        Long validLessonId = insertOneOffLesson(schema, oneOffA, occurrenceId, 805, 51, 61, 71, 81,
                "practice", "2026-03-20", "planned", 3, "10:00", "11:00", 1, 1);
        String lessons = table(schema, "lessons");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT one_off_lesson_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(oneOffA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");

        assertExactSqlFailure("P0001", PHYSICAL_SNAPSHOT_MISMATCH,
                () -> insertOneOffLesson(schema, oneOffB, occurrenceId, 805, 51, 61, 71, 81,
                        "practice", "2026-03-20", "cancelled", 3, "10:00", "11:00", 2, 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT one_off_lesson_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(oneOffA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");

        assertExactSqlFailure("P0001", PHYSICAL_SNAPSHOT_MISMATCH,
                () -> jdbc.update("UPDATE " + lessons
                        + " SET one_off_lesson_id = ? WHERE id = ?", oneOffB, validLessonId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + lessons
                        + " WHERE occurrence_id = ?", Integer.class, occurrenceId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT one_off_lesson_id FROM " + lessons
                        + " WHERE id = ?", Long.class, validLessonId)).isEqualTo(oneOffA);
        assertThat(jdbc.queryForObject("SELECT status::text FROM " + lessons
                        + " WHERE id = ?", String.class, validLessonId)).isEqualTo("planned");
    }

    @Test
    void assignmentIdIsRequiredAndPositiveForNewTemplates() {
        String schema = newSchema();
        flyway(schema, "17").migrate();

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(schema, "schedule_items")
                        + " (assignment_id, group_id, subject_id, semester_id, day_of_week, lesson_number,"
                        + " start_time, end_time, week_type) VALUES (0, 1, 2, 3, 1, 1, '09:00', '10:00', 'all')"))
                .isInstanceOf(DataAccessException.class);
    }

    private Long insertTemplate(String schema,
                                long assignmentId,
                                long groupId,
                                long subjectId,
                                long semesterId,
                                int lessonNumber,
                                String start,
                                String end) {
        return jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_items")
                        + " (assignment_id, group_id, subject_id, semester_id, day_of_week, lesson_number,"
                        + " start_time, end_time, week_type, room) VALUES (?, ?, ?, ?, 1, ?, ?::time, ?::time,"
                        + " 'all', 'A101') RETURNING id",
                Long.class, assignmentId, groupId, subjectId, semesterId, lessonNumber, start, end);
    }

    private Long insertLesson(String schema,
                              long templateId,
                              long occurrenceId,
                              long assignmentId,
                              long groupId,
                              long subjectId,
                              long semesterId,
                              long teacherId,
                              String lessonType,
                              String date,
                              String status,
                              int lessonNumber,
                              String start,
                              String end,
                              long generation,
                              long revision) {
        return jdbc.queryForObject("INSERT INTO " + table(schema, "lessons")
                        + " (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,"
                        + " assigned_teacher_id, lesson_type, lesson_number, start_time, end_time, generation, revision,"
                        + " date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::time, ?::time, ?, ?, ?::date, ?::lesson_status)"
                        + " RETURNING id",
                Long.class, templateId, occurrenceId, assignmentId, groupId, subjectId, semesterId,
                teacherId, lessonType, lessonNumber, start, end, generation, revision, date, status);
    }

    private Long insertOneOffLesson(String schema,
                                    long oneOffId,
                                    long occurrenceId,
                                    long assignmentId,
                                    long groupId,
                                    long subjectId,
                                    long semesterId,
                                    long teacherId,
                                    String lessonType,
                                    String date,
                                    String status,
                                    int lessonNumber,
                                    String start,
                                    String end,
                                    long generation,
                                    long revision) {
        return jdbc.queryForObject("INSERT INTO " + table(schema, "lessons")
                        + " (one_off_lesson_id, occurrence_id, assignment_id, group_id, subject_id, semester_id,"
                        + " assigned_teacher_id, lesson_type, lesson_number, start_time, end_time, generation, revision,"
                        + " date, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::time, ?::time, ?, ?, ?::date, ?::lesson_status)"
                        + " RETURNING id",
                Long.class, oneOffId, occurrenceId, assignmentId, groupId, subjectId, semesterId,
                teacherId, lessonType, lessonNumber, start, end, generation, revision, date, status);
    }

    private String newSchema() {
        String schema = "rct_b0_schedule_" + SCHEMA_SEQUENCE.incrementAndGet();
        adminJdbc.execute("CREATE SCHEMA " + quote(schema));
        dataSource = dataSourceForSchema(schema);
        jdbc = new JdbcTemplate(dataSource);
        return schema;
    }

    private DriverManagerDataSource dataSourceForSchema(String schema) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        String jdbcUrl = POSTGRES.getJdbcUrl();
        ds.setUrl(schema == null ? jdbcUrl : withCurrentSchema(jdbcUrl, schema));
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        return ds;
    }

    private static String withCurrentSchema(String jdbcUrl, String schema) {
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private Flyway flyway(String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static String table(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private static void assertExactSqlFailure(String expectedSqlState,
                                              String expectedMessage,
                                              Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> {
                    SqlFailure failure = nestedSqlFailure(error);
                    assertThat(failure.sqlState()).isEqualTo(expectedSqlState);
                    assertThat(failure.message()).contains(expectedMessage);
                });
    }

    private static SqlFailure nestedSqlFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return new SqlFailure(sqlException.getSQLState(), sqlException.getMessage());
            }
        }
        return new SqlFailure(null, null);
    }

    private record SqlFailure(String sqlState, String message) {
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
