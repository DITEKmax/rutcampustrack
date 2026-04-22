package ru.rutcampustrack.academic.migration;

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
 * M08 Группа 3 (P2-8/3) — Flyway migration integration tests для academic_db.
 *
 * <p>Покрывает 17 миграций V1..V17. Три template'а (D5 exception —
 * fresh container без reuse):
 * <ol>
 *   <li><b>freshInstallAppliesAllMigrations</b> — clean → migrate → applied>0, pending=0.</li>
 *   <li><b>checksumConsistency</b> — migrate → validate no-op, repeat migrate no-op.</li>
 *   <li><b>dataPreservationAcrossMigrations</b> — V1 baseline → INSERT group+user →
 *       V2..V17 → assert rows на месте. Покрывает V3 login_sequences, V7 avatars,
 *       V8 group unification, V9 groups_archived_at, V12 subjects_group_id и прочие
 *       schema evolutions.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("academic_db")
            .withUsername("rct_user")
            .withPassword("rct_dev_pass");
    // ВАЖНО: без .withReuse(true) — M08 D5 exception (fresh DB обязательна).

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
        flyway.validate();

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void dataPreservationAcrossMigrations() {
        MigrationTestUtils.clean(dataSource);

        // Step 1: V1 baseline.
        MigrationTestUtils.runMigrationsUpTo(dataSource, "1");

        // Step 2: INSERT group + user.
        // V1 schema: groups(id, name, code, is_active, created_at),
        //            users(id, login, password_hash, last_name, first_name,
        //                  ..., role, status, group_id, ...).
        Long groupId = jdbc.queryForObject(
                "INSERT INTO groups (name, code, is_active) " +
                        "VALUES ('Test Group M08', 'TG-M08', TRUE) RETURNING id",
                Long.class);
        assertThat(groupId).isNotNull();

        Long userId = jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, group_id) " +
                        "VALUES ('test_m08_user', '$2a$10$XXXXXXXXXXXXXXXXXXXXXX', " +
                        "'Testov', 'Test', 'student'::user_role, ?) RETURNING id",
                Long.class, groupId);
        assertThat(userId).isNotNull();

        // Step 3: apply V2..V17.
        MigrationTestUtils.runAllMigrations(dataSource);

        // Step 4: assert rows все ещё на месте.
        Integer groupCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM groups WHERE id = ?",
                Integer.class, groupId);
        assertThat(groupCount)
                .as("V1 group должна сохраниться после V2..V17 миграций")
                .isEqualTo(1);

        Integer userCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class, userId);
        assertThat(userCount)
                .as("V1 user должен сохраниться после V2..V17 миграций")
                .isEqualTo(1);
    }
}
