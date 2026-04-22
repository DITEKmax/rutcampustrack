package ru.rutcampustrack.schedule.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.shared.testcontainers.MigrationTestUtils;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 Группа 3 (P2-8/3) — Flyway migration integration tests для schedule_db.
 *
 * <p>Три template'а покрытия (D5 exception — fresh container без reuse):
 * <ol>
 *   <li><b>freshInstallAppliesAllMigrations</b> — clean → migrate → assert applied>0, pending=0.
 *       Гарантирует, что весь набор миграций V1..V12 проходит с нуля.</li>
 *   <li><b>checksumConsistency</b> — migrate → validate (без изменений).
 *       Гарантирует, что repeatable migrations не изменились после первого applied.</li>
 *   <li><b>softDeleteMigrationPreservesData</b> — migrate V1 → INSERT row → migrate V2..V12 →
 *       assert row всё ещё есть. Data-preservation invariant для V3 (drop_teacher_id)
 *       и V4 (one_off_lessons добавления).</li>
 * </ol>
 *
 * <p>Не использует {@code AbstractScheduleIntegrationTest} — не нужен Spring context,
 * работает напрямую с jdbc. Также НЕ включает {@code .withReuse(true)} — fresh container
 * обязателен (reuse сохранит flyway_schema_history между прогонами, Flyway не перезапустит
 * миграции с нуля, баг в clean-install scenario скроется).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("schedule_db")
            .withUsername("rct_user")
            .withPassword("rct_dev_pass");
    // ВАЖНО: без .withReuse(true) — M08 D5 exception (FlywayMigrationIT требует fresh DB).

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        POSTGRES.start();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void freshInstallAppliesAllMigrations() {
        MigrationTestUtils.clean(dataSource);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive();
        assertThat(result.success).isTrue();

        MigrationInfo[] pending = flyway.info().pending();
        assertThat(pending).isEmpty();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getState().isApplied()).isTrue();
    }

    @Test
    void checksumConsistencyAfterFullMigrate() {
        MigrationTestUtils.clean(dataSource);
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.migrate();

        // validate() бросит FlywayValidateException если checksums расходятся.
        // Второй прогон validate — no-op если всё гладко.
        flyway.validate();

        // Повторный migrate() должен быть no-op (0 applied).
        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void dataPreservationAcrossMigrations() {
        MigrationTestUtils.clean(dataSource);

        // Step 1: накатить V1 (baseline schema).
        MigrationTestUtils.runMigrationsUpTo(dataSource, "1");

        // Step 2: INSERT в schedule_items. V1 baseline schema:
        //   schedule_items(id BIGSERIAL PK, group_id, subject_id, teacher_id,
        //     semester_id NOT NULL, day_of_week, lesson_number, start_time,
        //     end_time, week_type week_type DEFAULT 'all', room, is_active, created_at)
        // V3 дропает teacher_id — проверяем что row с teacher_id переживёт drop column.
        jdbc.update(
                "INSERT INTO schedule_items " +
                        "(group_id, subject_id, teacher_id, semester_id, " +
                        "day_of_week, lesson_number, start_time, end_time, week_type, room) " +
                        "VALUES (100, 200, 300, 1, 1, 1, " +
                        "'09:00'::time, '10:30'::time, 'all'::week_type, 'A101')");

        Integer insertedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_items WHERE group_id = 100",
                Integer.class);
        assertThat(insertedCount).isEqualTo(1);

        // Step 3: накатить оставшиеся миграции (V2..V12).
        MigrationTestUtils.runAllMigrations(dataSource);

        // Step 4: assert row всё ещё на месте. V3 дропает teacher_id —
        // нужно запрашивать без этого поля.
        Integer afterMigrateCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM schedule_items WHERE group_id = 100",
                Integer.class);
        assertThat(afterMigrateCount)
                .as("V1 insert должен сохраниться после V2..V12 миграций")
                .isEqualTo(1);
    }
}
