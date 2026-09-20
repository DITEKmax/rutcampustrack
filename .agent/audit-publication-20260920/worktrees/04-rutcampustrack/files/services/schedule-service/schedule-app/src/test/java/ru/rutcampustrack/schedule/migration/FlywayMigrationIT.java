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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flyway V1-V17 migration and source-preservation checks for schedule_db. */
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
        assertThat(current.getVersion().getVersion()).isEqualTo("17");
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

        Long rowId = jdbc.queryForObject("INSERT INTO " + table(schema, "schedule_items")
                        + " (group_id, subject_id, teacher_id, semester_id, day_of_week, lesson_number,"
                        + " start_time, end_time, week_type, room)"
                        + " VALUES (100, 200, 300, 1, 1, 1, '09:00'::time, '10:30'::time, 'all'::week_type, 'A101')"
                        + " RETURNING id", Long.class);
        flyway(schema, "16").migrate();

        assertThat(columnCount(schema, "schedule_items", "assignment_id")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "schedule_items")
                        + " WHERE id = ?", Integer.class, rowId)).isEqualTo(1);

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("V17");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "schedule_items")
                        + " WHERE id = ?", Integer.class, rowId)).isEqualTo(1);
        assertThat(columnCount(schema, "schedule_items", "assignment_id")).isZero();
        assertThat(columnCount(schema, "lessons", "occurrence_id")).isZero();
        assertThat(historyCount(schema, "17")).isZero();
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

    private int historyCount(String schema, String version) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "flyway_schema_history")
                + " WHERE version = ?", Integer.class, version);
    }

    private int columnCount(String schema, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schema, table, column);
    }

    private static String table(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
