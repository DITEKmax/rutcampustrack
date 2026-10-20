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
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
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
        long binding = dateBinding(901L);
        long other = dateBinding(902L);
        long lessonBinding = lessonBinding(903L);
        var checksums =
                jdbc.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        var upgraded = flyway(schema, null);
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        var original =
                jdbc.queryForObject(
                        "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE"
                                + " binding_id=?",
                        String.class,
                        binding);
        for (String mutation :
                java.util.List.of(
                        "actor_id=99",
                        "payload_hash=decode(repeat('ab',32),'hex')",
                        "group_id=11",
                        "subject_id=21",
                        "semester_id=31",
                        "revision=0",
                        "placement_date=DATE '2026-04-02'")) {
            assertThatThrownBy(
                            () ->
                                    jdbc.update(
                                            "UPDATE lesson_homework_bindings SET "
                                                    + mutation
                                                    + " WHERE binding_id = ?",
                                            binding))
                    .isInstanceOf(DataAccessException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings"
                                            + " binding WHERE binding_id=?",
                                    String.class,
                                    binding))
                    .isEqualTo(original);
        }
        var edit = java.util.UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO homework_placement_operations(operation_id,binding_id,homework_id,actor_id,request_key,
    command_hash,group_id,subject_id,semester_id,state,accepted_binding,accepted_revision,target_mode,target_date)
VALUES (?,?,901,42,?,decode(repeat('00',32),'hex'),10,20,30,'APPLIED_AWAITING_ACK','{}'::jsonb,2,'DATE',DATE '2026-04-02')
""",
                edit,
                binding,
                java.util.UUID.randomUUID());
        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                dataSource));
        // An exact receipt still rejects a different target/revision.
        for (String wrong :
                java.util.List.of(
                        "placement_date=DATE '2026-04-03',revision=2",
                        "placement_date=DATE '2026-04-02',revision=3")) {
            assertThatThrownBy(
                            () ->
                                    tx.executeWithoutResult(
                                            status -> {
                                                jdbc.queryForObject(
                                                        "SELECT"
                                                            + " set_config('rutcampustrack.homework_edit_operation_id',?,true)",
                                                        String.class,
                                                        edit.toString());
                                                jdbc.update(
                                                        "UPDATE lesson_homework_bindings SET "
                                                                + wrong
                                                                + ",pending_edit_operation_id=?"
                                                                + " WHERE binding_id=?",
                                                        edit,
                                                        binding);
                                            }))
                    .isInstanceOf(DataAccessException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings"
                                            + " binding WHERE binding_id=?",
                                    String.class,
                                    binding))
                    .isEqualTo(original);
        }
        tx.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT set_config('rutcampustrack.homework_edit_operation_id',?,true)",
                            String.class,
                            edit.toString());
                    jdbc.update(
                            "UPDATE lesson_homework_bindings SET placement_date=DATE"
                                    + " '2026-04-02',revision=2,pending_edit_operation_id=? WHERE"
                                    + " binding_id=?",
                            edit,
                            binding);
                });
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lesson_homework_bindings SET"
                                            + " pending_edit_operation_id=NULL WHERE binding_id=?",
                                        binding))
                .isInstanceOf(DataAccessException.class);
        jdbc.update(
                "UPDATE homework_placement_operations SET"
                        + " state='ACKNOWLEDGED',acknowledged_at=NOW() WHERE operation_id=?",
                edit);
        jdbc.update(
                "UPDATE lesson_homework_bindings SET pending_edit_operation_id=NULL WHERE"
                        + " binding_id=?",
                binding);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revision FROM lesson_homework_bindings WHERE binding_id=?",
                                Long.class,
                                binding))
                .isEqualTo(2L);
        // Upgrade must also retain exact edit admission for an existing LESSON publication.
        var lessonBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE"
                                + " binding_id=?",
                        String.class,
                        lessonBinding);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE lesson_homework_bindings SET"
                                            + " binding_mode='DATE',occurrence_id=NULL,current_lesson_id=NULL,placement_lesson_number=NULL,placement_date=DATE"
                                            + " '2026-04-02',revision=2 WHERE binding_id=?",
                                        lessonBinding))
                .isInstanceOf(DataAccessException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings"
                                        + " binding WHERE binding_id=?",
                                String.class,
                                lessonBinding))
                .isEqualTo(lessonBefore);
        var lessonEdit = java.util.UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO homework_placement_operations(operation_id,binding_id,homework_id,actor_id,request_key,
    command_hash,group_id,subject_id,semester_id,state,accepted_binding,accepted_revision,target_mode,target_date)
VALUES (?,?,903,42,?,decode(repeat('00',32),'hex'),10,20,30,'APPLIED_AWAITING_ACK','{}'::jsonb,2,'DATE',DATE '2026-04-02')
""",
                lessonEdit,
                lessonBinding,
                java.util.UUID.randomUUID());
        tx.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT set_config('rutcampustrack.homework_edit_operation_id',?,true)",
                            String.class,
                            lessonEdit.toString());
                    jdbc.update(
                            "UPDATE lesson_homework_bindings SET"
                                + " binding_mode='DATE',occurrence_id=NULL,current_lesson_id=NULL,placement_lesson_number=NULL,placement_date=DATE"
                                + " '2026-04-02',revision=2,pending_edit_operation_id=? WHERE"
                                + " binding_id=?",
                            lessonEdit,
                            lessonBinding);
                });
        jdbc.update(
                "UPDATE homework_placement_operations SET"
                        + " state='ACKNOWLEDGED',acknowledged_at=NOW() WHERE operation_id=?",
                lessonEdit);
        jdbc.update(
                "UPDATE lesson_homework_bindings SET pending_edit_operation_id=NULL WHERE"
                        + " binding_id=?",
                lessonBinding);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT original_binding_mode FROM lesson_homework_bindings WHERE"
                                        + " binding_id=?",
                                String.class,
                                lessonBinding))
                .isEqualTo("LESSON");
        var deletion = java.util.UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO schedule_subject_deletion_participants(operation_id,subject_id,semester_id,group_id,actor_id,
    participant_digest,manifest,command_manifest,manifest_digest,state)
SELECT ?,20,30,10,42,repeat('0',64),jsonb_build_array(jsonb_build_object('kind','lesson_homework_bindings',
    'row_hash',encode(public.digest(convert_to(to_jsonb(binding)::text,'UTF8'),'sha256'),'hex'))),
    decode('00','hex'),repeat('0',64),'COMMITTING'
FROM lesson_homework_bindings binding WHERE binding_id=?
""",
                deletion,
                binding);
        var otherBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings binding WHERE"
                                + " binding_id=?",
                        String.class,
                        other);
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        status -> {
                                            jdbc.queryForObject(
                                                    "SELECT"
                                                        + " set_config('rutcampustrack.subject_delete_operation_id',?,true)",
                                                    String.class,
                                                    deletion.toString());
                                            jdbc.update(
                                                    "UPDATE lesson_homework_bindings SET"
                                                        + " state='ARCHIVED',homework_id=NULL,revision=revision+1"
                                                        + " WHERE binding_id=?",
                                                    other);
                                        }))
                .isInstanceOf(DataAccessException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(binding)::text FROM lesson_homework_bindings"
                                        + " binding WHERE binding_id=?",
                                String.class,
                                other))
                .isEqualTo(otherBefore);
        tx.executeWithoutResult(
                status -> {
                    jdbc.queryForObject(
                            "SELECT"
                                + " set_config('rutcampustrack.subject_delete_operation_id',?,true)",
                            String.class,
                            deletion.toString());
                    jdbc.update(
                            "UPDATE lesson_homework_bindings SET"
                                    + " state='ARCHIVED',homework_id=NULL,revision=revision+1 WHERE"
                                    + " binding_id=?",
                            binding);
                });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT state FROM lesson_homework_bindings WHERE binding_id=?",
                                String.class,
                                binding))
                .isEqualTo("ARCHIVED");
        assertThat(
                        jdbc.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank LIMIT ?",
                                checksums.size()))
                .isEqualTo(checksums);
        assertThat(tableCount(schema, "schedule_subject_deletion_participants")).isEqualTo(1);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    private long lessonBinding(long homework) {
        jdbc.update(
                """
INSERT INTO schedule_assignment_fences(assignment_id,group_id,subject_id,semester_id,assigned_teacher_id,
    lesson_type,valid_from,cap_until_exclusive,creation_cap_until_exclusive)
VALUES (501,10,20,30,700,'lecture',DATE '2026-01-01',DATE '2200-01-01',DATE '2200-01-01')
""");
        long origin =
                jdbc.queryForObject(
                        """
INSERT INTO schedule_one_off_lessons(group_id,subject_id,semester_id,date,lesson_number,classroom,created_by)
VALUES (10,20,30,DATE '2026-04-01',1,'retained room',42) RETURNING id
""",
                        Long.class);
        long occurrence =
                jdbc.queryForObject(
                        """
INSERT INTO lesson_occurrences(one_off_lesson_id,occurrence_date,assignment_id,group_id,subject_id,
    semester_id,assigned_teacher_id,lesson_type,generation,revision)
VALUES (?,DATE '2026-04-01',501,10,20,30,700,'lecture',1,1) RETURNING id
""",
                        Long.class,
                        origin);
        long lesson =
                jdbc.queryForObject(
                        """
INSERT INTO lessons(one_off_lesson_id,occurrence_id,assignment_id,group_id,subject_id,semester_id,
    assigned_teacher_id,lesson_type,lesson_number,day_of_week,start_time,end_time,
    room_snapshot,week_type_snapshot,generation,revision,date,status,is_geo_blocked)
VALUES (?,?,501,10,20,30,700,'lecture',1,3,TIME '08:30',TIME '09:50','retained room','all',1,1,DATE '2026-04-01','planned',FALSE) RETURNING id
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
        return jdbc.queryForObject(
                """
INSERT INTO lesson_homework_bindings(occurrence_id,current_lesson_id,homework_id,actor_id,request_key,
    payload_hash,state,binding_mode) VALUES (?,?,?,42,?,decode(repeat('00',32),'hex'),'ACTIVE','LESSON') RETURNING binding_id
""",
                Long.class,
                occurrence,
                lesson,
                homework,
                java.util.UUID.randomUUID());
    }

    private long dateBinding(long homework) {
        return jdbc.queryForObject(
                """
INSERT INTO lesson_homework_bindings(homework_id,actor_id,request_key,payload_hash,state,
    binding_mode,group_id,subject_id,semester_id,placement_date,original_binding_mode,original_date)
VALUES (?,42,?,decode(repeat('00',32),'hex'),'ACTIVE','DATE',10,20,30,DATE '2026-04-01','DATE',DATE '2026-04-01')
RETURNING binding_id
""",
                Long.class,
                homework,
                java.util.UUID.randomUUID());
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
