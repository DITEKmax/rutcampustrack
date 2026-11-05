package ru.rutcampustrack.academic.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Disposable production migrations plus the actual browser fixture callback. */
@Testcontainers
class SyntheticAcademicBaselineIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("academic_db")
                    .withUsername("rct_migrator");

    private JdbcTemplate jdbc;

    @BeforeEach
    void freshProductionDatabase() {
        var source =
                new DriverManagerDataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP SCHEMA public CASCADE");
        jdbc.execute("CREATE SCHEMA public");
        jdbc.execute(
                "DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE"
                        + " rolname='rct_academic_runtime') THEN CREATE ROLE rct_academic_runtime;"
                        + " END IF; END $$");
        Flyway.configure().dataSource(source).locations("classpath:db/academic-migration")
                .load().migrate();
    }

    @Test
    void freshSeedPreservesApprovedCampusAndItsAdvancedSequence() throws Exception {
        jdbc.update("UPDATE campus_settings SET name='Approved retained name' WHERE id=1");
        jdbc.execute("SELECT setval('campus_settings_id_seq',100,TRUE)");
        Map<String, Object> campus = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1");

        seed();

        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1")).isEqualTo(campus);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_profiles", Long.class))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM e2e_synthetic_baseline", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT nextval('campus_settings_id_seq')", Long.class))
                .isEqualTo(101L);

        jdbc.update("UPDATE campus_settings SET radius_m=250 WHERE id=1");
        jdbc.update("UPDATE groups SET name='Legitimate later change' WHERE id=1");
        seed();

        assertThat(jdbc.queryForObject("SELECT radius_m FROM campus_settings WHERE id=1", Integer.class))
                .isEqualTo(250);
        assertThat(jdbc.queryForObject("SELECT name FROM groups WHERE id=1", String.class))
                .isEqualTo("Legitimate later change");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_profiles", Long.class))
                .isEqualTo(3L);
    }

    @Test
    void unmarkedPartialDomainDataIsRejectedWithoutAnySeedWrites() {
        jdbc.update("INSERT INTO groups(id,name) VALUES(9,'Existing unmarked group')");

        assertThatThrownBy(this::seed).hasMessageContaining("Unmarked Academic baseline must be empty");

        assertThat(jdbc.queryForObject("SELECT name FROM groups WHERE id=9", String.class))
                .isEqualTo("Existing unmarked group");
        assertUnseeded();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "coordinates", "extra"})
    void unapprovedCampusInventoryIsRejectedWithoutOverwritingIt(String state) {
        switch (state) {
            case "missing" -> jdbc.update("DELETE FROM campus_settings");
            case "coordinates" -> jdbc.update("UPDATE campus_settings SET lat=55.788204 WHERE id=1");
            case "extra" -> jdbc.update(
                    "INSERT INTO campus_settings(id,name,lat,lng,radius_m) VALUES(9,'Other',51,32,300)");
            default -> throw new AssertionError("unknown fixture state");
        }
        var before = jdbc.queryForList("SELECT * FROM campus_settings ORDER BY id");

        assertThatThrownBy(this::seed)
                .hasMessageContaining("Academic baseline requires the approved production campus");

        assertThat(jdbc.queryForList("SELECT * FROM campus_settings ORDER BY id")).isEqualTo(before);
        assertUnseeded();
    }

    private void assertUnseeded() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_profiles", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT to_regclass('public.e2e_synthetic_baseline') IS NULL", Boolean.class)).isTrue();
    }

    private void seed() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("repository root unavailable");
        String sql = Files.readString(root.resolve(
                "tests/e2e/infra/baseline/academic/afterMigrate__e2e_synthetic_academic_baseline.sql"));
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET rct.e2e.synthetic_baseline='browser-v2-split-20261018-v1'");
                statement.execute(sql);
            }
            return null;
        });
    }
}
