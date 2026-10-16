package ru.rutcampustrack.schedule.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/** Flyway V1-V21 migration and source-preservation checks for schedule_db. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class FlywayMigrationIT {

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
    void freshInstallAppliesAllMigrations() {
        String schema = newSchema();
        Flyway flyway = flyway(schema, null);

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive();
        assertThat(result.success).isTrue();
        assertThat(flyway.info().pending()).isEmpty();
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getVersion())
                .isEqualTo(
                        java.util.Arrays.stream(flyway.info().all())
                                .map(MigrationInfo::getVersion)
                                .filter(java.util.Objects::nonNull)
                                .max(java.util.Comparator.naturalOrder())
                                .orElseThrow());
        assertThat(tableCount(schema, "lesson_transfer_operations")).isEqualTo(1);
        assertThat(tableCount(schema, "lesson_transfer_binding_batches")).isEqualTo(1);
        assertThat(tableCount(schema, "lesson_transfer_participant_receipts")).isEqualTo(1);
    }

    @Test
    void v30ToV31RetainsExistingOneOffRowsAndPhysicalGuards() {
        String schema = newSchema();
        flyway(schema, "30").migrate();
        jdbc.update(
                """
INSERT INTO schedule_assignment_fences
    (assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,
     lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive)
VALUES (501,10,20,30,700,'lecture',DATE '2026-01-01',DATE '2200-01-01',DATE '2200-01-01')
""");
        long origin =
                jdbc.queryForObject(
                        """
INSERT INTO schedule_one_off_lessons
    (group_id,subject_id,semester_id,date,lesson_number,classroom,created_by)
VALUES (10,20,30,DATE '2026-10-15',1,'retained room',42) RETURNING id
""",
                        Long.class);
        long occurrence =
                jdbc.queryForObject(
                        """
INSERT INTO lesson_occurrences
    (one_off_lesson_id,occurrence_date,assignment_id,group_id,subject_id,semester_id,
     assigned_teacher_id,lesson_type,generation,revision)
VALUES (?,DATE '2026-10-15',501,10,20,30,700,'lecture',1,1) RETURNING id
""",
                        Long.class,
                        origin);
        long lesson =
                jdbc.queryForObject(
                        """
INSERT INTO lessons
    (one_off_lesson_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,
     assigned_teacher_id,lesson_type,lesson_number,day_of_week,start_time,end_time,
     room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked)
VALUES (?,?,501,10,20,30,700,'lecture',1,4,TIME '08:30',TIME '10:00',
        'retained room','all',1,1,DATE '2026-10-15','planned',FALSE) RETURNING id
""",
                        Long.class,
                        origin,
                        occurrence);
        jdbc.update(
                "UPDATE lesson_occurrences SET current_lesson_id=? WHERE id=?", lesson, occurrence);
        jdbc.update(
                "UPDATE schedule_one_off_lessons SET physical_lesson_id=? WHERE id=?",
                lesson,
                origin);
        jdbc.update(
                """
                INSERT INTO lesson_lifecycle_entries
                    (occurrence_id,revision,action,lesson_id,generation,actor_id,occurred_at)
                VALUES (?,1,'CREATED',?,1,42,NOW())
                """,
                occurrence,
                lesson);
        String snapshotSql =
                """
SELECT jsonb_build_object('origin',to_jsonb(origin),'occurrence',to_jsonb(occurrence),
    'lesson',to_jsonb(lesson),'history',to_jsonb(history))::text
  FROM schedule_one_off_lessons origin
  JOIN lesson_occurrences occurrence ON occurrence.one_off_lesson_id=origin.id
  JOIN lessons lesson ON lesson.id=occurrence.current_lesson_id
  JOIN lesson_lifecycle_entries history ON history.occurrence_id=occurrence.id
 WHERE occurrence.id=?
""";
        String before = jdbc.queryForObject(snapshotSql, String.class, occurrence);
        Flyway upgraded = flyway(schema, "31");
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject(snapshotSql, String.class, occurrence)).isEqualTo(before);
        assertThat(historyCount(schema, "30")).isEqualTo(1);
        assertThat(historyCount(schema, "31")).isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lessons SET"
                                            + " assignment_id=502,assigned_teacher_id=701,revision=revision+1"
                                            + " WHERE id=?",
                                        lesson))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOf(SQLException.class)
                .hasMessageContaining(
                        "physical lesson provenance changes require an exact replacement ledger"
                                + " row")
                .satisfies(
                        cause ->
                                assertThat(((SQLException) cause).getSQLState())
                                        .isEqualTo("P0001"));
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lessons SET room_snapshot='unreceipted"
                                                + " room',revision=revision+1 WHERE id=?",
                                        lesson))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("physical lesson identity snapshot is immutable")
                .satisfies(
                        cause ->
                                assertThat(((SQLException) cause).getSQLState())
                                        .isEqualTo("P0001"));
        assertThat(jdbc.queryForObject(snapshotSql, String.class, occurrence)).isEqualTo(before);
    }

    @Test
    void checksumConsistencyAfterFullMigrate() {
        String schema = newSchema();
        Flyway flyway = flyway(schema, null);
        flyway.migrate();

        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void v17SourceGuardPreservesV1ToV16RowSchemaAndHistory() {
        String schema = newSchema();
        flyway(schema, "1").migrate();

        Long rowId =
                jdbc.queryForObject(
                        "INSERT INTO "
                                + table(schema, "schedule_items")
                                + " (group_id, subject_id, teacher_id, semester_id, day_of_week,"
                                + " lesson_number, start_time, end_time, week_type, room) VALUES"
                                + " (100, 200, 300, 1, 1, 1, '09:00'::time, '10:30'::time,"
                                + " 'all'::week_type, 'A101') RETURNING id",
                        Long.class);
        flyway(schema, "16").migrate();

        assertThat(columnCount(schema, "schedule_items", "assignment_id")).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM "
                                        + table(schema, "schedule_items")
                                        + " WHERE id = ?",
                                Integer.class,
                                rowId))
                .isEqualTo(1);

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("V17");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM "
                                        + table(schema, "schedule_items")
                                        + " WHERE id = ?",
                                Integer.class,
                                rowId))
                .isEqualTo(1);
        assertThat(columnCount(schema, "schedule_items", "assignment_id")).isZero();
        assertThat(columnCount(schema, "lessons", "occurrence_id")).isZero();
        assertThat(historyCount(schema, "17")).isZero();
    }

    @Test
    void v31ToV32KeepsExactPlacementReceiptGuardAndOldMigrationChecksums() {
        String schema = newSchema();
        flyway(schema, "31").migrate();
        String before =
                jdbc.queryForObject(
                        "SELECT"
                            + " pg_get_functiondef('protect_lesson_homework_binding_identity()'::REGPROCEDURE)",
                        String.class);
        var checksums =
                jdbc.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        var upgraded = flyway(schema, null);
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        String after =
                jdbc.queryForObject(
                        "SELECT"
                            + " pg_get_functiondef('protect_lesson_homework_binding_identity()'::REGPROCEDURE)",
                        String.class);
        assertThat(
                        after.replace(
                                        "    IF TG_OP = 'UPDATE' AND"
                                            + " schedule_subject_delete_binding_authorized(to_jsonb(OLD),to_jsonb(NEW))"
                                            + " THEN RETURN NEW; END IF;\n",
                                        "")
                                .equals(before))
                .as("V24 durable placement identity and acknowledgement guard retained")
                .isTrue();
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank LIMIT ?",
                                checksums.size()))
                .isEqualTo(checksums);
        assertThat(tableCount(schema, "schedule_subject_deletion_participants")).isEqualTo(1);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void v32DdlRollbackRetainsV31GuardAndAllowsCleanUpgrade() throws Exception {
        String schema = newSchema();
        flyway(schema, "31").migrate();
        String before =
                jdbc.queryForObject(
                        "SELECT"
                            + " pg_get_functiondef('protect_lesson_homework_binding_identity()'::REGPROCEDURE)",
                        String.class);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var ddl =
                        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                                new org.springframework.core.io.ClassPathResource(
                                        "db/migration/V32__subject_deletion_participant.sql"));
                ddl.setSeparator(
                        org.springframework.jdbc.datasource.init.ScriptUtils
                                .EOF_STATEMENT_SEPARATOR);
                ddl.populate(connection);
                connection.rollback();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
        assertThat(tableCount(schema, "schedule_subject_deletion_participants")).isZero();
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT"
                                            + " pg_get_functiondef('protect_lesson_homework_binding_identity()'::REGPROCEDURE)",
                                        String.class)
                                .equals(before))
                .isTrue();
        assertThat(flyway(schema, null).migrate().migrationsExecuted).isEqualTo(1);
        flyway(schema, null).validate();
    }

    private String newSchema() {
        String schema = "rct_b0_flyway_" + SCHEMA_SEQUENCE.incrementAndGet();
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
        var configuration =
                Flyway.configure()
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

    private int historyCount(String schema, String version) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM "
                        + table(schema, "flyway_schema_history")
                        + " WHERE version = ?",
                Integer.class,
                version);
    }

    private int columnCount(String schema, String table, String column) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class,
                schema,
                table,
                column);
    }

    private int tableCount(String schema, String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = ? AND table_name = ?",
                Integer.class,
                schema,
                table);
    }

    private static String table(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
