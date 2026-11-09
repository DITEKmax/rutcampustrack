package ru.rutcampustrack.academic.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.map.CampusMapAdminRepository;
import ru.rutcampustrack.academic.map.CampusMapReadRepository;
import ru.rutcampustrack.academic.map.CampusMapReadService;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.sql.Connection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

/** Forward production configuration on the independent Academic migration chain. */
class CampusProductionConfigurationMigrationIT {
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    private static String pgUrl;
    private static String pgUser;
    private static String pgPassword;

    @BeforeAll
    static void startDatabase() {
        pgUrl = System.getenv("RCT_GEO_PG_URL");
        if (pgUrl == null || pgUrl.isBlank()) {
            POSTGRES.start();
            pgUrl = POSTGRES.getJdbcUrl();
            pgUser = POSTGRES.getUsername();
            pgPassword = POSTGRES.getPassword();
        } else {
            if (!pgUrl.startsWith("jdbc:postgresql:"))
                throw new IllegalArgumentException(
                        "Geo migration proof requires PostgreSQL JDBC URL");
            java.net.URI uri = java.net.URI.create(pgUrl.substring("jdbc:".length()));
            if (uri.getUserInfo() != null
                    || uri.getPort() != 15449
                    || !"/postgres".equals(uri.getPath())
                    || uri.getQuery() != null
                    || !java.util.List.of("127.0.0.1", "localhost", "[::1]")
                            .contains(uri.getHost()))
                throw new IllegalArgumentException(
                        "Geo migration proof requires own loopback PostgreSQL");
            pgUser = "postgres";
            pgPassword =
                    ""; // Own native fixture uses loopback trust, never production credentials.
        }
    }

    @AfterAll
    static void stopDatabase() {
        if (POSTGRES.isRunning()) POSTGRES.stop();
    }

    private static final AtomicInteger DATABASE_SEQUENCE = new AtomicInteger();
    private static final String GEOFENCE = "V18__expand_primary_campus_geofence.sql";
    private static final String MAP = "V16__initialize_empty_campus_map_catalog.sql";

    @Test
    void existingTwoHundredMetersUpgradesWithoutRewritingMigrationHistoryOrOtherRows() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        flyway(source, "17").migrate();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT radius_m FROM campus_settings WHERE id=1", Integer.class))
                .isEqualTo(200);
        jdbc.update("INSERT INTO groups(id,name) VALUES(9001,'Retained academic group')");
        jdbc.update(
                "INSERT INTO campus_settings(id,name,lat,lng,radius_m) VALUES(9,'Other"
                        + " campus',51,32,300)");
        jdbc.execute("SELECT setval('campus_settings_id_seq',100,TRUE)");
        var group = jdbc.queryForMap("SELECT * FROM groups WHERE id=9001");
        var other = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9");
        Integer checksum =
                jdbc.queryForObject(
                        "SELECT checksum FROM flyway_schema_history WHERE version='15'",
                        Integer.class);
        var latest = flyway(source, null);
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
        assertApproved(jdbc);
        String policyEvidence = System.getenv("RCT_GEO_POLICY_JSON");
        if (policyEvidence != null && !policyEvidence.isBlank()) {
            try {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(policyEvidence),
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(
                                        jdbc.queryForMap(
                                                "SELECT lat,lng,radius_m FROM campus_settings WHERE"
                                                        + " id=1")));
            } catch (java.io.IOException unavailable) {
                throw new IllegalStateException(
                        "Cannot retain own migration policy evidence", unavailable);
            }
        }
        assertThat(jdbc.queryForMap("SELECT * FROM groups WHERE id=9001")).isEqualTo(group);
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9")).isEqualTo(other);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT checksum FROM flyway_schema_history WHERE version='15'",
                                Integer.class))
                .isEqualTo(checksum);
        var primary = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1");
        replay(jdbc, GEOFENCE);
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1")).isEqualTo(primary);
        latest.validate();
        assertThat(latest.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT nextval('campus_settings_id_seq')", Long.class))
                .isEqualTo(101L);
    }

    @Test
    void legacyForwardSqlUsesTheSameCampusTableAndIsIdempotentOnExistingAndEmptySettings() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO campus_settings(id,name,lat,lng,radius_m) VALUES(1,'Retained"
                        + " name',55.788328,37.606637,200)");
        replayResource(jdbc, "db/migration/V64__expand_primary_campus_geofence.sql");
        assertApproved(jdbc);
        var primary = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1");
        replayResource(jdbc, "db/migration/V64__expand_primary_campus_geofence.sql");
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1")).isEqualTo(primary);
        JdbcTemplate empty = new JdbcTemplate(baseline());
        replayResource(empty, "db/migration/V64__expand_primary_campus_geofence.sql");
        assertApproved(empty);
    }

    @Test
    void emptyProductionUpgradeProvidesApprovedGeofenceAndHonestEmptyManifest() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);

        Flyway upgraded = flyway(source, null);
        upgraded.migrate();

        assertApproved(jdbc);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_settings", Long.class))
                .isEqualTo(1);
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdIncludingArchived(100L)).thenReturn(Optional.of(mock(User.class)));
        var service = new CampusMapReadService(new CampusMapReadRepository(jdbc), users);
        var claims =
                new InternalJwtClaims(
                        100L, UUID.randomUUID(), 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false);
        var result = service.readManifest(0L, claims);
        assertThat(result.manifest().revision()).isEqualTo(1L);
        assertThat(result.manifest().schemaVersion()).isEqualTo(1);
        assertThat(result.manifest().validationPolicyVersion()).isEqualTo(1);
        assertThat(result.manifest().buildings()).isEmpty();
        assertThat(service.readManifest(1L, claims).unchanged()).isTrue();

        Map<String, Object> before = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1");
        replay(jdbc, GEOFENCE);
        replay(jdbc, MAP);
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=1")).isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM campus_map_catalog_revision", Long.class))
                .isEqualTo(1);
        upgraded.validate();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT nextval('campus_settings_id_seq')", Long.class))
                .isGreaterThan(1L);
    }

    @Test
    void existingCampusNameAndUnrelatedRowsSurviveWhileApprovedConfigurationWins() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO campus_settings(id,name,lat,lng,radius_m,updated_at)"
                    + " VALUES(1,'Сохранённое имя',55.7699,37.7039,500,'2020-01-01 00:00:00+00'),"
                    + " (9,'Другой кампус',51,32,300,'2020-02-01 00:00:00+00')");
        jdbc.execute("SELECT setval('campus_settings_id_seq',100,TRUE)");
        Map<String, Object> other = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9");

        flyway(source, null).migrate();

        assertApproved(jdbc);
        assertThat(jdbc.queryForObject("SELECT name FROM campus_settings WHERE id=1", String.class))
                .isEqualTo("Сохранённое имя");
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9")).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT nextval('campus_settings_id_seq')", Long.class))
                .isEqualTo(101L);
    }

    @Test
    void missingPrimaryRowDoesNotOverwriteAnotherCampusAndSequenceAdvancesPastAllRows() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO campus_settings(id,name,lat,lng,radius_m)"
                        + " VALUES(9,'Другой',51,32,300)");
        Map<String, Object> other = jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9");

        flyway(source, null).migrate();

        assertApproved(jdbc);
        assertThat(jdbc.queryForMap("SELECT * FROM campus_settings WHERE id=9")).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT nextval('campus_settings_id_seq')", Long.class))
                .isEqualTo(10L);
    }

    @Test
    void existingCatalogAndBuildingArePreservedExactly() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO campus_map_catalog_revision"
                        + " (revision,schema_version,validation_policy_version,is_current)"
                        + " VALUES(7,2,3,TRUE)");
        jdbc.update(
                "INSERT INTO campus_map_building(code,label,display_order) VALUES('A','Корпус',0)");
        Map<String, Object> catalog = jdbc.queryForMap("SELECT * FROM campus_map_catalog_revision");
        Map<String, Object> building = jdbc.queryForMap("SELECT * FROM campus_map_building");

        flyway(source, null).migrate();
        replay(jdbc, MAP);

        assertThat(jdbc.queryForMap("SELECT * FROM campus_map_catalog_revision"))
                .isEqualTo(catalog);
        assertThat(jdbc.queryForMap("SELECT * FROM campus_map_building")).isEqualTo(building);
    }

    @Test
    void partialMapGraphIsNotSilentlyRepaired() {
        DataSource source = baseline();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.update(
                "INSERT INTO campus_map_building(code,label,display_order) VALUES('A','Корпус',0)");

        flyway(source, null).migrate();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM campus_map_catalog_revision", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_map_building", Long.class))
                .isEqualTo(1);
    }

    @Test
    void mapBootstrapWaitsForAdminBeforeTakingConflictingTableLocks() throws Exception {
        DataSource source = baseline();
        var worker = Executors.newSingleThreadExecutor();
        try (Connection admin = source.getConnection();
                Connection migration = source.getConnection()) {
            admin.setAutoCommit(false);
            migration.setAutoCommit(false);
            var adminJdbc = new JdbcTemplate(new SingleConnectionDataSource(admin, true));
            var migrationJdbc = new JdbcTemplate(new SingleConnectionDataSource(migration, true));
            adminJdbc.execute("SET LOCAL statement_timeout='5s'");
            migrationJdbc.execute("SET LOCAL statement_timeout='5s'");
            int migrationPid =
                    migrationJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
            var repository = new CampusMapAdminRepository(adminJdbc);
            repository.lockCatalogForWrite();
            repository.insertBuilding("A", "Concurrent building", 0);

            var pending =
                    worker.submit(
                            () -> {
                                ScriptUtils.executeSqlScript(
                                        migration,
                                        new ClassPathResource("db/academic-migration/" + MAP));
                                migration.commit();
                                return null;
                            });
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                boolean waitingForAdmin = false;
                while (!waitingForAdmin && System.nanoTime() < deadline) {
                    waitingForAdmin =
                            Boolean.TRUE.equals(
                                    adminJdbc.queryForObject(
                                            "SELECT EXISTS(SELECT 1 FROM pg_locks WHERE pid=? AND"
                                                    + " locktype='advisory' AND NOT granted)",
                                            Boolean.class,
                                            migrationPid));
                    if (!waitingForAdmin) {
                        Thread.sleep(10);
                    }
                }
                assertThat(waitingForAdmin).as("migration waits on admin advisory lock").isTrue();
                repository.advanceCatalogRevision();
                admin.commit();
                pending.get(5, TimeUnit.SECONDS);
            } finally {
                admin.rollback();
            }
        } finally {
            worker.shutdownNow();
        }
        JdbcTemplate jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM campus_map_building", Long.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM campus_map_catalog_revision", Long.class))
                .isEqualTo(1);
    }

    private static void assertApproved(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForMap("SELECT lat,lng,radius_m FROM campus_settings WHERE id=1"))
                .containsEntry("lat", 55.788328)
                .containsEntry("lng", 37.606637)
                .containsEntry("radius_m", 400);
    }

    private static DataSource baseline() {
        String database = "rct_geo_1110_" + java.util.UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(pgUrl, pgUser, pgPassword);
        new JdbcTemplate(admin).execute("CREATE DATABASE " + database);
        var source =
                new DriverManagerDataSource(
                        pgUrl.substring(0, pgUrl.lastIndexOf('/') + 1) + database,
                        pgUser,
                        pgPassword);
        flyway(source, "14").migrate();
        return source;
    }

    private static Flyway flyway(DataSource source, String target) {
        var configuration =
                Flyway.configure().dataSource(source).locations("classpath:db/academic-migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static void replay(JdbcTemplate jdbc, String filename) {
        replayResource(jdbc, "db/academic-migration/" + filename);
    }

    private static void replayResource(JdbcTemplate jdbc, String path) {
        jdbc.execute(
                (ConnectionCallback<Void>)
                        connection -> {
                            boolean previousAutoCommit = connection.getAutoCommit();
                            connection.setAutoCommit(false);
                            try {
                                ScriptUtils.executeSqlScript(
                                        connection, new ClassPathResource(path));
                                connection.commit();
                            } catch (RuntimeException error) {
                                connection.rollback();
                                throw error;
                            } finally {
                                connection.setAutoCommit(previousAutoCommit);
                            }
                            return null;
                        });
    }
}
