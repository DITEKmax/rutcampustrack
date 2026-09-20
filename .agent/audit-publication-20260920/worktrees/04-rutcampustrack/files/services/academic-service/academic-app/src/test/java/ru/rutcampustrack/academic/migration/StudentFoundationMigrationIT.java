package ru.rutcampustrack.academic.migration;

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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL behavior checks for the B0 academic V24-V26 chain.
 *
 * <p>Every method owns a fresh schema in one fresh PostgreSQL 16 container;
 * no Flyway clean/drop is used. The first concurrency test reconstructs the
 * deferred child-only trigger from the pre-edit V25 raw preimage to record the
 * actual PostgreSQL write-skew reproduction. The following test exercises the
 * corrected migration state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class StudentFoundationMigrationIT {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_student_b0_academic")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();
    private static final String OWNER_A = "decode(repeat('aa', 32), 'hex')";
    private static final String OWNER_B = "decode(repeat('bb', 32), 'hex')";
    private static final String OWNER_C = "decode(repeat('cc', 32), 'hex')";
    private static final String PAYLOAD_A = "decode(repeat('11', 32), 'hex')";
    private static final String PAYLOAD_B = "decode(repeat('22', 32), 'hex')";
    private static final String PAYLOAD_C = "decode(repeat('33', 32), 'hex')";
    private static final String READY_METADATA_MISMATCH =
            "ready campus map format metadata does not match its asset";
    private static final String PUBLISHED_FORMAT_IMMUTABLE =
            "published campus map plan format is immutable";
    private static final String DEMAND_DEDUPE_MISMATCH =
            "campus map demand dedupe does not match its open intent";
    private static final int PRE_FIX_BARRIER_KEY_CLASS = 0x52435442;
    private static final int PRE_FIX_BARRIER_KEY_OBJECT = 0x563236;

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
        assertThat(flyway.info().current()).isNotNull();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void readyMapAssetsRequireExactMetadataAndRemainImmutable() {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        String content = "decode('89504e470d0a1a0a', 'hex')";
        String hash = "public.digest(" + content + ", 'sha256')";
        Long assetId = insertAsset(schema, fixture.planVersionId(), "PNG", "image/png",
                content, 8, hash, 640, 480);

        jdbc.update("INSERT INTO " + table(schema, "campus_map_plan_format")
                        + " (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)"
                        + " VALUES (?, 'PNG', 'READY', 'image/png', ?, 8, " + hash + ", 640, 480)",
                fixture.planVersionId(), assetId);

        Map<String, Object> metadata = jdbc.queryForMap("""
                SELECT a.content_type AS asset_mime,
                       f.content_type AS format_mime,
                       a.bytes AS asset_bytes,
                       f.bytes AS format_bytes,
                       a.sha256 AS asset_hash,
                       f.sha256 AS format_hash
                FROM %s a JOIN %s f ON f.asset_id = a.id
                WHERE a.id = ?
                """.formatted(table(schema, "campus_map_asset"), table(schema, "campus_map_plan_format")), assetId);
        assertThat(metadata.get("asset_mime")).isEqualTo("image/png");
        assertThat(metadata.get("format_mime")).isEqualTo("image/png");
        assertThat(((Number) metadata.get("asset_bytes")).longValue()).isEqualTo(8L);
        assertThat(((Number) metadata.get("format_bytes")).longValue()).isEqualTo(8L);
        assertThat((byte[]) metadata.get("format_hash"))
                .containsExactly((byte[]) metadata.get("asset_hash"));

        String formats = table(schema, "campus_map_plan_format");
        int formatCountBeforeMetadataMismatch = jdbc.queryForObject("SELECT COUNT(*) FROM " + formats
                + " WHERE plan_version_id = ?", Integer.class, fixture.planVersionId());
        assertExactSqlFailure("P0001", READY_METADATA_MISMATCH, () -> jdbc.update("INSERT INTO "
                        + formats
                        + " (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)"
                        + " VALUES (?, 'PNG', 'READY', 'image/png', ?, 8, " + hash + ", 641, 480)",
                fixture.planVersionId(), assetId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + formats
                + " WHERE plan_version_id = ?", Integer.class, fixture.planVersionId()))
                .isEqualTo(formatCountBeforeMetadataMismatch);

        assertSqlFailure("P0001", () -> jdbc.update("INSERT INTO "
                        + table(schema, "campus_map_plan_format")
                        + " (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)"
                        + " VALUES (?, 'PNG', 'READY', 'image/png', ?, 7, " + hash + ", 640, 480)",
                fixture.planVersionId(), assetId));
        assertSqlFailure("P0001", () -> jdbc.update("INSERT INTO "
                        + table(schema, "campus_map_plan_format")
                        + " (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)"
                        + " VALUES (?, 'PNG', 'READY', 'image/png', ?, 8, decode(repeat('00', 32), 'hex'), 640, 480)",
                fixture.planVersionId(), assetId));
        assertSqlFailure("P0001", () -> jdbc.update("INSERT INTO "
                        + table(schema, "campus_map_plan_format")
                        + " (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)"
                        + " VALUES (?, 'PNG', 'READY', 'image/svg+xml', ?, 8, " + hash + ", 640, 480)",
                fixture.planVersionId(), assetId));

        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + table(schema, "campus_map_asset")
                        + " SET content = content WHERE id = ?", assetId));
        assertSqlFailure("P0001", () -> jdbc.update("DELETE FROM " + table(schema, "campus_map_asset")
                        + " WHERE id = ?", assetId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "campus_map_asset")
                + " WHERE id = ?", Integer.class, assetId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "campus_map_plan_format")
                + " WHERE plan_version_id = ? AND format = 'PNG'", Integer.class, fixture.planVersionId()))
                .isEqualTo(1);
    }

    @Test
    void publishedCampusMapFreezesPlanIdentityAndGraphButDraftTransitionsRemainAllowed() {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        Long secondFloorId = jdbc.queryForObject("INSERT INTO " + table(schema, "campus_map_floor")
                        + " (building_id, code, label, display_order) VALUES (?, 'F2', 'Second floor', 2) RETURNING id",
                Long.class, fixture.buildingId());
        Long catalogRevisionId = jdbc.queryForObject("INSERT INTO "
                        + table(schema, "campus_map_catalog_revision")
                        + " (schema_version, validation_policy_version) VALUES (1, 1) RETURNING id",
                Long.class);
        String plan = table(schema, "campus_map_plan_version");
        String formats = table(schema, "campus_map_plan_format");

        jdbc.update("UPDATE " + plan + " SET label = 'draft label' WHERE id = ?", fixture.planVersionId());
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + plan
                        + " SET floor_id = ? WHERE id = ?", secondFloorId, fixture.planVersionId()));
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + plan
                        + " SET version = 2 WHERE id = ?", fixture.planVersionId()));
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + plan
                        + " SET catalog_revision_id = ? WHERE id = ?", catalogRevisionId, fixture.planVersionId()));

        Long formatId = jdbc.queryForObject("INSERT INTO " + formats
                        + " (plan_version_id, format, state, content_type)"
                        + " VALUES (?, 'PNG', 'ABSENT', 'image/png') RETURNING id",
                Long.class, fixture.planVersionId());
        jdbc.update("UPDATE " + formats + " SET state = 'PROCESSING' WHERE id = ?", formatId);
        jdbc.update("UPDATE " + formats + " SET state = 'FAILED' WHERE id = ?", formatId);

        String contentA = "decode('89504e470d0a1a0a', 'hex')";
        String hashA = "public.digest(" + contentA + ", 'sha256')";
        Long assetA = insertAsset(schema, fixture.planVersionId(), "PNG", "image/png",
                contentA, 8, hashA, 640, 480);
        jdbc.update("UPDATE " + formats + " SET state = 'READY', content_type = 'image/png', asset_id = ?,"
                        + " bytes = 8, sha256 = " + hashA + ", width = 640, height = 480 WHERE id = ?",
                assetA, formatId);

        String contentB = "decode('89504e470d0a1a09', 'hex')";
        String hashB = "public.digest(" + contentB + ", 'sha256')";
        Long assetB = insertAsset(schema, fixture.planVersionId(), "PNG", "image/png",
                contentB, 8, hashB, 640, 480);
        jdbc.update("UPDATE " + plan + " SET published_at = TIMESTAMPTZ '2026-01-01 00:00:00+00'"
                + " WHERE id = ?", fixture.planVersionId());

        int formatGraphCountBeforeRejectedInsert = jdbc.queryForObject("SELECT COUNT(*) FROM " + formats
                + " WHERE plan_version_id = ?", Integer.class, fixture.planVersionId());
        int assetGraphCountBeforeRejectedInsert = jdbc.queryForObject("SELECT COUNT(*) FROM "
                + table(schema, "campus_map_asset") + " WHERE plan_version_id = ?", Integer.class,
                fixture.planVersionId());
        Long existingFormatAssetId = jdbc.queryForObject("SELECT asset_id FROM " + formats
                + " WHERE id = ?", Long.class, formatId);
        String existingFormatState = jdbc.queryForObject("SELECT state FROM " + formats
                + " WHERE id = ?", String.class, formatId);
        Long existingAssetBytes = jdbc.queryForObject("SELECT bytes FROM "
                + table(schema, "campus_map_asset") + " WHERE id = ?", Long.class, assetA);
        byte[] existingAssetHash = jdbc.queryForObject("SELECT sha256 FROM "
                + table(schema, "campus_map_asset") + " WHERE id = ?", byte[].class, assetA);
        assertExactSqlFailure("P0001", PUBLISHED_FORMAT_IMMUTABLE, () -> jdbc.update("INSERT INTO " + formats
                        + " (plan_version_id, format, state, content_type)"
                        + " VALUES (?, 'SVG', 'ABSENT', 'image/svg+xml')", fixture.planVersionId()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + formats
                + " WHERE plan_version_id = ?", Integer.class, fixture.planVersionId()))
                .isEqualTo(formatGraphCountBeforeRejectedInsert);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "campus_map_asset")
                + " WHERE plan_version_id = ?", Integer.class, fixture.planVersionId()))
                .isEqualTo(assetGraphCountBeforeRejectedInsert);
        assertThat(jdbc.queryForObject("SELECT asset_id FROM " + formats
                + " WHERE id = ?", Long.class, formatId)).isEqualTo(existingFormatAssetId);
        assertThat(jdbc.queryForObject("SELECT state FROM " + formats
                + " WHERE id = ?", String.class, formatId)).isEqualTo(existingFormatState);
        assertThat(jdbc.queryForObject("SELECT bytes FROM " + table(schema, "campus_map_asset")
                + " WHERE id = ?", Long.class, assetA)).isEqualTo(existingAssetBytes);
        assertThat((byte[]) jdbc.queryForObject("SELECT sha256 FROM " + table(schema, "campus_map_asset")
                + " WHERE id = ?", byte[].class, assetA)).containsExactly(existingAssetHash);

        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + plan
                        + " SET label = 'published edit' WHERE id = ?", fixture.planVersionId()));
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + plan
                        + " SET published_at = NULL WHERE id = ?", fixture.planVersionId()));
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + formats
                        + " SET asset_id = ?, bytes = 8, sha256 = " + hashB + " WHERE id = ?", assetB, formatId));
        assertSqlFailure("P0001", () -> jdbc.update("DELETE FROM " + formats + " WHERE id = ?", formatId));
        assertThat(jdbc.queryForObject("SELECT asset_id FROM " + formats + " WHERE id = ?", Long.class, formatId))
                .isEqualTo(assetA);
    }

    @Test
    void publicationSerializesAgainstConcurrentDraftFormatEdit() throws Exception {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        String plan = table(schema, "campus_map_plan_version");
        String formats = table(schema, "campus_map_plan_format");
        Long formatId = jdbc.queryForObject("INSERT INTO " + formats
                        + " (plan_version_id, format, state, content_type)"
                        + " VALUES (?, 'PNG', 'ABSENT', 'image/png') RETURNING id",
                Long.class, fixture.planVersionId());

        Connection publisher = dataSource.getConnection();
        Connection editor = dataSource.getConnection();
        publisher.setAutoCommit(false);
        editor.setAutoCommit(false);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        boolean published = false;
        try {
            try (var statement = publisher.prepareStatement("UPDATE " + plan
                    + " SET published_at = TIMESTAMPTZ '2026-01-02 00:00:00+00' WHERE id = ?")) {
                statement.setLong(1, fixture.planVersionId());
                statement.executeUpdate();
            }
            Future<String> blockedEdit = executor.submit(() -> {
                try (var lockTimeout = editor.createStatement()) {
                    lockTimeout.execute("SET LOCAL lock_timeout = '1s'");
                }
                try (var statement = editor.prepareStatement("UPDATE " + formats
                        + " SET state = 'PROCESSING' WHERE id = ?")) {
                    statement.setLong(1, formatId);
                    statement.executeUpdate();
                    editor.commit();
                    return "committed";
                } catch (SQLException exception) {
                    rollbackOrFail(editor, exception);
                    return sqlState(exception);
                }
            });
            assertThat(blockedEdit.get(20, TimeUnit.SECONDS)).isEqualTo("55P03");
            publisher.commit();
            published = true;
            String postPublicationEdit;
            try (var statement = editor.prepareStatement("UPDATE " + formats
                    + " SET state = 'PROCESSING' WHERE id = ?")) {
                statement.setLong(1, formatId);
                statement.executeUpdate();
                editor.commit();
                postPublicationEdit = "committed";
            } catch (SQLException exception) {
                rollbackOrFail(editor, exception);
                postPublicationEdit = sqlState(exception);
            }
            assertThat(postPublicationEdit).isEqualTo("P0001");
        } finally {
            if (!published) {
                rollbackOrFail(publisher, new AssertionError("publication transaction did not commit"));
            }
            executor.shutdownNow();
            publisher.close();
            editor.close();
        }

        assertThat(jdbc.queryForObject("SELECT state FROM " + formats + " WHERE id = ?", String.class, formatId))
                .isEqualTo("ABSENT");
    }

    @Test
    void openIntentIdentityAndConflictingPayloadAreRejected() {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        UUID intentId = UUID.randomUUID();
        insertOpenIntent(schema, fixture, OWNER_A, intentId, PAYLOAD_A,
                "DATE '2999-01-01'", "TIMESTAMPTZ '2999-01-01 12:00:00+00'",
                "TIMESTAMPTZ '2999-01-03 12:00:00+00'");

        String intents = table(schema, "campus_map_open_intent");
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + intents
                        + " SET payload_hash = " + PAYLOAD_B + " WHERE owner_hmac = " + OWNER_A
                        + " AND intent_id = ?", intentId));
        assertSqlFailure("P0001", () -> jdbc.update("UPDATE " + intents
                        + " SET accepted_at = TIMESTAMPTZ '2999-01-01 13:00:00+00'"
                        + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", intentId));
        assertSqlFailure("23505", () -> insertOpenIntent(schema, fixture, OWNER_A, intentId, PAYLOAD_B,
                "DATE '2999-01-01'", "TIMESTAMPTZ '2999-01-01 12:00:00+00'",
                "TIMESTAMPTZ '2999-01-03 12:00:00+00'"));

        assertThat(jdbc.queryForObject("SELECT payload_hash FROM " + intents
                        + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", byte[].class, intentId))
                .containsExactly(hexBytes("11"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + intents
                + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", Integer.class, intentId)).isEqualTo(1);
    }

    @Test
    void demandDedupeIsExactlyOnceAndIncrementsFloorDayAggregateOnce() {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        UUID intentId = UUID.randomUUID();
        String day = "DATE '2999-01-01'";
        String acceptedAt = "TIMESTAMPTZ '2999-01-01 12:00:00+00'";
        insertOpenIntent(schema, fixture, OWNER_A, intentId, PAYLOAD_A, day, acceptedAt,
                "TIMESTAMPTZ '2999-01-03 12:00:00+00'");
        insertDemandDedupe(schema, fixture.floorId(), OWNER_A, intentId, day, acceptedAt);

        String dedupe = table(schema, "campus_map_floor_demand_dedupe");
        String demand = table(schema, "campus_map_floor_daily_demand");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", Integer.class, intentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT open_count FROM " + demand
                        + " WHERE floor_id = ? AND utc_day = DATE '2999-01-01'", Long.class,
                fixture.floorId())).isEqualTo(1L);

        List<Map<String, Object>> dailyDemandBeforeInvalidAttempts = jdbc.queryForList(
                "SELECT utc_day, open_count FROM " + demand
                        + " WHERE floor_id = ? ORDER BY utc_day", fixture.floorId());
        UUID missingIntentId = UUID.randomUUID();
        assertExactSqlFailure("P0001", DEMAND_DEDUPE_MISMATCH,
                () -> insertDemandDedupe(schema, fixture.floorId(), OWNER_B, missingIntentId,
                        day, acceptedAt));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_B + " AND intent_id = ?", Integer.class,
                missingIntentId)).isZero();
        assertThat(jdbc.queryForList("SELECT utc_day, open_count FROM " + demand
                + " WHERE floor_id = ? ORDER BY utc_day", fixture.floorId()))
                .containsExactlyElementsOf(dailyDemandBeforeInvalidAttempts);

        UUID mismatchedIntentId = UUID.randomUUID();
        String secondDay = "DATE '2999-01-02'";
        String secondAcceptedAt = "TIMESTAMPTZ '2999-01-02 12:00:00+00'";
        insertOpenIntent(schema, fixture, OWNER_B, mismatchedIntentId, PAYLOAD_B,
                secondDay, secondAcceptedAt, "TIMESTAMPTZ '2999-01-04 12:00:00+00'");
        assertExactSqlFailure("P0001", DEMAND_DEDUPE_MISMATCH,
                () -> insertDemandDedupe(schema, fixture.floorId(), OWNER_B, mismatchedIntentId,
                        "DATE '2999-01-03'", secondAcceptedAt));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_B + " AND intent_id = ?", Integer.class,
                mismatchedIntentId)).isZero();
        assertThat(jdbc.queryForList("SELECT utc_day, open_count FROM " + demand
                + " WHERE floor_id = ? ORDER BY utc_day", fixture.floorId()))
                .containsExactlyElementsOf(dailyDemandBeforeInvalidAttempts);

        assertSqlFailure("23505", () -> insertDemandDedupe(schema, fixture.floorId(), OWNER_A, intentId,
                day, acceptedAt));
        jdbc.update("INSERT INTO " + dedupe
                        + " (owner_hmac, floor_id, utc_day, intent_id, accepted_at)"
                        + " VALUES (" + OWNER_A + ", ?, DATE '2999-01-01', ?, " + acceptedAt + ")"
                        + " ON CONFLICT (owner_hmac, floor_id, utc_day) DO NOTHING",
                fixture.floorId(), intentId);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", Integer.class, intentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT open_count FROM " + demand
                        + " WHERE floor_id = ? AND utc_day = DATE '2999-01-01'", Long.class,
                fixture.floorId())).isEqualTo(1L);
    }

    @Test
    void intentAndDailyRetentionUseIndependentDeadlines() {
        String schema = newSchema();
        flyway(schema, "26").migrate();
        CampusMapFixture fixture = campusMapFixture(schema);
        String intents = table(schema, "campus_map_open_intent");
        String dedupe = table(schema, "campus_map_floor_demand_dedupe");
        UUID futureIntentId = UUID.randomUUID();
        insertOpenIntent(schema, fixture, OWNER_A, futureIntentId, PAYLOAD_A,
                "DATE '2999-01-01'", "TIMESTAMPTZ '2999-01-01 12:00:00+00'",
                "TIMESTAMPTZ '2999-01-03 12:00:00+00'");
        insertDemandDedupe(schema, fixture.floorId(), OWNER_A, futureIntentId,
                "DATE '2999-01-01'", "TIMESTAMPTZ '2999-01-01 12:00:00+00'");
        assertSqlFailure("P0001", () -> jdbc.update("DELETE FROM " + intents
                        + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", futureIntentId));
        assertSqlFailure("P0001", () -> jdbc.update("DELETE FROM " + dedupe
                        + " WHERE owner_hmac = " + OWNER_A + " AND intent_id = ?", futureIntentId));

        String currentUtcDayMinusTwo = "((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 2)";
        String currentUtcStartMinusTwo = "(((CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 2)::timestamp"
                + " AT TIME ZONE 'UTC')";
        UUID boundaryIntentId = UUID.randomUUID();
        insertOpenIntent(schema, fixture, OWNER_B, boundaryIntentId, PAYLOAD_B,
                currentUtcDayMinusTwo, currentUtcStartMinusTwo,
                "(" + currentUtcStartMinusTwo + " + INTERVAL '48 hours')");
        insertDemandDedupe(schema, fixture.floorId(), OWNER_B, boundaryIntentId,
                currentUtcDayMinusTwo, currentUtcStartMinusTwo);
        assertSqlFailure("P0001", () -> jdbc.update("DELETE FROM " + dedupe
                        + " WHERE owner_hmac = " + OWNER_B + " AND intent_id = ?", boundaryIntentId));
        assertThat(jdbc.update("DELETE FROM " + intents
                + " WHERE owner_hmac = " + OWNER_B + " AND intent_id = ?", boundaryIntentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_B + " AND intent_id = ?", Integer.class, boundaryIntentId)).isEqualTo(1);

        UUID expiredIntentId = UUID.randomUUID();
        insertOpenIntent(schema, fixture, OWNER_C, expiredIntentId, PAYLOAD_C,
                "DATE '2000-01-01'", "TIMESTAMPTZ '2000-01-01 12:00:00+00'",
                "TIMESTAMPTZ '2000-01-03 12:00:00+00'");
        insertDemandDedupe(schema, fixture.floorId(), OWNER_C, expiredIntentId,
                "DATE '2000-01-01'", "TIMESTAMPTZ '2000-01-01 12:00:00+00'");
        assertThat(jdbc.update("DELETE FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_C + " AND intent_id = ?", expiredIntentId)).isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM " + intents
                + " WHERE owner_hmac = " + OWNER_C + " AND intent_id = ?", expiredIntentId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + dedupe
                + " WHERE owner_hmac = " + OWNER_C + " AND intent_id = ?", Integer.class, expiredIntentId)).isZero();
        assertThat(jdbc.queryForObject("SELECT open_count FROM "
                        + table(schema, "campus_map_floor_daily_demand")
                        + " WHERE floor_id = ? AND utc_day = DATE '2000-01-01'", Long.class, fixture.floorId()))
                .isEqualTo(1L);
    }

    @Test
    void inconsistentLegacyHeadmanAbortsV24BeforeAnySchemaChange() {
        String schema = newSchema();
        flyway(schema, "23").migrate();

        jdbc.update("UPDATE " + table(schema, "users")
                        + " SET is_headman = TRUE WHERE login = 'teacher'");

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("V24");

        assertThat(columnCount(schema, "users", "roles_version")).isZero();
        assertThat(tableCount(schema, "user_role_grants")).isZero();
        assertThat(historyCount(schema, "24")).isZero();
        assertThat(jdbc.queryForObject("SELECT is_headman FROM " + table(schema, "users")
                        + " WHERE login = 'teacher'", Boolean.class)).isTrue();
    }

    @Test
    void v24ConvertsOnlyExplicitRolesAndProtectsAuthorityRows() {
        String schema = newSchema();
        flyway(schema, "24").migrate();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "user_role_grants"),
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "user_role_grants")
                        + " WHERE user_id = (SELECT id FROM " + table(schema, "users")
                        + " WHERE login = 'student') AND role = 'headman'", Integer.class))
                .isEqualTo(1);

        Long teacherId = jdbc.queryForObject("SELECT id FROM " + table(schema, "users")
                + " WHERE login = 'teacher'", Long.class);
        Long studentId = jdbc.queryForObject("SELECT id FROM " + table(schema, "users")
                + " WHERE login = 'student'", Long.class);
        Long teacherGrant = jdbc.queryForObject("SELECT id FROM " + table(schema, "user_role_grants")
                + " WHERE user_id = ? AND role = 'teacher'", Long.class, teacherId);
        Long studentGrant = jdbc.queryForObject("SELECT id FROM " + table(schema, "user_role_grants")
                + " WHERE user_id = ? AND role = 'student'", Long.class, studentId);

        Long rolesVersion = jdbc.queryForObject("SELECT roles_version FROM " + table(schema, "users")
                + " WHERE id = ?", Long.class, teacherId);
        jdbc.update("UPDATE " + table(schema, "user_role_grants")
                + " SET status = 'suspended' WHERE id = ?", teacherGrant);
        assertThat(jdbc.queryForObject("SELECT roles_version FROM " + table(schema, "users")
                        + " WHERE id = ?", Long.class, teacherId))
                .isEqualTo(rolesVersion + 1);

        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "users")
                        + " SET roles_version = 0 WHERE id = ?", teacherId))
                .isInstanceOf(DataAccessException.class);

        UUID sid = UUID.randomUUID();
        UUID jti = UUID.randomUUID();
        jdbc.update("INSERT INTO " + table(schema, "auth_sessions")
                        + " (sid, user_id, active_role_grant_id, session_version, current_refresh_jti,"
                        + " refresh_expires_at, created_at, last_seen_at, auth_method)"
                        + " VALUES (?, ?, ?, 1, ?, NOW() + INTERVAL '1 day', NOW(), NOW(), 'PASSWORD')",
                sid, teacherId, teacherGrant, jti);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(schema, "auth_sessions")
                        + " (sid, user_id, active_role_grant_id, session_version, current_refresh_jti,"
                        + " refresh_expires_at, created_at, last_seen_at, auth_method)"
                        + " VALUES (?, ?, ?, 1, ?, NOW() + INTERVAL '1 day', NOW(), NOW(), 'PASSWORD')",
                UUID.randomUUID(), teacherId, studentGrant, UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);

        jdbc.update("INSERT INTO " + table(schema, "account_security_events")
                        + " (user_id, sid, event_type, occurred_at, auth_method)"
                        + " VALUES (?, ?, 'LOGIN', NOW(), 'PASSWORD')", teacherId, sid);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "account_security_events")
                        + " SET event_type = 'LOGOUT_ALL' WHERE user_id = ?", teacherId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table(schema, "account_security_events")
                        + " WHERE user_id = ?", teacherId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void legacyTeacherSubjectGroupAbortsV25BeforeDdl() {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        Long subjectId = insertSubject(schema, "Legacy assignment source");

        jdbc.update("INSERT INTO " + table(schema, "teacher_subject_groups")
                        + " (teacher_id, subject_id, group_id, semester_id) VALUES (2, ?, 1, 1)",
                subjectId);

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("teacher_subject_groups");

        assertThat(tableCount(schema, "subject_lesson_types")).isZero();
        assertThat(tableCount(schema, "assignments")).isZero();
        assertThat(historyCount(schema, "25")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "teacher_subject_groups")
                        + " WHERE subject_id = ?", Integer.class, subjectId)).isEqualTo(1);
    }

    @Test
    void legacyHomeworkAbortsV25BeforeBindingColumns() {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        Long subjectId = insertSubject(schema, "Legacy homework source");

        jdbc.update("INSERT INTO " + table(schema, "homeworks")
                        + " (group_id, subject_id, semester_id, lesson_date, lesson_number,"
                        + " title, published_by) VALUES (1, ?, 1, DATE '2026-03-01', 1, 'old', 2)",
                subjectId);

        assertThatThrownBy(() -> flyway(schema, null).migrate())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("homeworks");

        assertThat(columnCount(schema, "homeworks", "binding_id")).isZero();
        assertThat(historyCount(schema, "25")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "homeworks")
                        + " WHERE subject_id = ?", Integer.class, subjectId)).isEqualTo(1);
    }

    @Test
    void subjectMustHaveATypeEvenWhenTheParentIsTheOnlyChangedRow() {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        flyway(schema, "25").migrate();

        assertThatThrownBy(() -> insertSubject(schema, "Missing type source"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void preFixDeferredChildOnlyCheckReproducesConcurrentZero() throws Exception {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        Long subjectId = insertSubject(schema, "Pre-fix race source");
        flyway(schema, "25").migrate();
        jdbc.update("INSERT INTO " + table(schema, "subject_lesson_types")
                + " (subject_id, lesson_type) VALUES (?, 'practice')", subjectId);

        installPreFixDeferredChildOnlyTrigger(schema);
        RaceResult result = raceDeletingDifferentTypes(schema, subjectId, true);

        // The test-only post-check barrier makes the recorded PostgreSQL
        // child-only deferred EXISTS reproduction deterministic: both unsafe
        // checks finish before either independent delete can commit.
        assertThat(result.commits()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "subject_lesson_types")
                        + " WHERE subject_id = ?", Integer.class, subjectId)).isZero();
    }

    @Test
    void correctedParentAndChildChecksAllowOnlyOneConcurrentDelete() throws Exception {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        Long subjectId = insertSubject(schema, "Corrected race source");
        flyway(schema, "25").migrate();
        jdbc.update("INSERT INTO " + table(schema, "subject_lesson_types")
                + " (subject_id, lesson_type) VALUES (?, 'practice')", subjectId);

        RaceResult result = raceDeletingDifferentTypes(schema, subjectId, false);

        assertThat(result.commits()).isEqualTo(1);
        assertThat(result.expectedFailures()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "subject_lesson_types")
                        + " WHERE subject_id = ?", Integer.class, subjectId)).isEqualTo(1);
    }

    @Test
    void assignmentIntervalsAndHomeworkBindingIdentityAreEnforced() {
        String schema = newSchema();
        flyway(schema, "24").migrate();
        Long subjectId = insertSubject(schema, "Assignment rules");
        flyway(schema, "25").migrate();

        jdbc.update("INSERT INTO " + table(schema, "assignments")
                        + " (teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from)"
                        + " VALUES (2, ?, 1, 1, 'lecture', DATE '2026-02-01')", subjectId);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(schema, "assignments")
                        + " (teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from)"
                        + " VALUES (2, ?, 1, 1, 'lecture', DATE '2026-03-01')", subjectId))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("INSERT INTO " + table(schema, "assignments")
                        + " (teacher_id, subject_id, group_id, semester_id, lesson_type, valid_from)"
                        + " VALUES (3, ?, 1, 1, 'lecture', DATE '2026-03-01')", subjectId);
        Long assignmentId = jdbc.queryForObject("SELECT id FROM " + table(schema, "assignments")
                + " WHERE teacher_id = 2", Long.class);
        jdbc.update("UPDATE " + table(schema, "assignments")
                + " SET valid_until_exclusive = DATE '2026-04-01' WHERE id = ?", assignmentId);
        jdbc.update("UPDATE " + table(schema, "assignments")
                + " SET valid_until_exclusive = DATE '2026-03-15' WHERE id = ?", assignmentId);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "assignments")
                        + " SET valid_until_exclusive = DATE '2026-05-01' WHERE id = ?", assignmentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table(schema, "assignments")
                        + " WHERE id = ?", assignmentId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "assignments")
                        + " SET id = ? WHERE id = ?", assignmentId + 10000, assignmentId))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + table(schema, "homeworks")
                        + " (group_id, subject_id, semester_id, lesson_date, lesson_number, title,"
                        + " published_by, binding_id, actor_id, request_key, payload_hash)"
                        + " VALUES (1, ?, 1, DATE '2026-03-01', 1, 'bad', 2, 0, 2, ?, decode(repeat('11', 32), 'hex'))",
                subjectId, UUID.randomUUID())).isInstanceOf(DataAccessException.class);

        UUID requestKey = UUID.randomUUID();
        jdbc.update("INSERT INTO " + table(schema, "homeworks")
                        + " (group_id, subject_id, semester_id, lesson_date, lesson_number, title,"
                        + " published_by, binding_id, actor_id, request_key, payload_hash)"
                        + " VALUES (1, ?, 1, DATE '2026-03-01', 1, 'good', 2, 700, 2, ?,"
                        + " decode(repeat('22', 32), 'hex'))", subjectId, requestKey);
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "homeworks")
                        + " SET binding_id = 701 WHERE binding_id = 700"))
                .isInstanceOf(DataAccessException.class);
        jdbc.update("UPDATE " + table(schema, "homeworks")
                + " SET publication_state = 'ACTIVE' WHERE binding_id = 700");
        assertThatThrownBy(() -> jdbc.update("UPDATE " + table(schema, "homeworks")
                        + " SET publication_state = 'PENDING' WHERE binding_id = 700"))
                .isInstanceOf(DataAccessException.class);
    }

    private String newSchema() {
        String schema = "rct_b0_academic_" + SCHEMA_SEQUENCE.incrementAndGet();
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

    private CampusMapFixture campusMapFixture(String schema) {
        Long buildingId = jdbc.queryForObject("INSERT INTO " + table(schema, "campus_map_building")
                        + " (code, label, display_order) VALUES ('B-MAIN', 'Main building', 1) RETURNING id",
                Long.class);
        Long floorId = jdbc.queryForObject("INSERT INTO " + table(schema, "campus_map_floor")
                        + " (building_id, code, label, display_order) VALUES (?, 'F1', 'First floor', 1) RETURNING id",
                Long.class, buildingId);
        Long planVersionId = jdbc.queryForObject("INSERT INTO " + table(schema, "campus_map_plan_version")
                        + " (floor_id, label) VALUES (?, 'Draft plan') RETURNING id", Long.class, floorId);
        return new CampusMapFixture(buildingId, floorId, planVersionId);
    }

    private Long insertAsset(String schema,
                             long planVersionId,
                             String format,
                             String contentType,
                             String contentExpression,
                             long bytes,
                             String hashExpression,
                             int width,
                             int height) {
        return jdbc.queryForObject("INSERT INTO " + table(schema, "campus_map_asset")
                        + " (plan_version_id, format, content_type, content, bytes, sha256, width, height)"
                        + " VALUES (?, ?, ?, " + contentExpression + ", ?, " + hashExpression + ", ?, ?) RETURNING id",
                Long.class, planVersionId, format, contentType, bytes, width, height);
    }

    private void insertOpenIntent(String schema,
                                  CampusMapFixture fixture,
                                  String ownerExpression,
                                  UUID intentId,
                                  String payloadExpression,
                                  String dayExpression,
                                  String acceptedAtExpression,
                                  String expiresAtExpression) {
        jdbc.update("INSERT INTO " + table(schema, "campus_map_open_intent")
                        + " (owner_hmac, intent_id, building_id, floor_id, payload_hash, accepted_utc_day,"
                        + " accepted_at, expires_at) VALUES (" + ownerExpression + ", ?, ?, ?, "
                        + payloadExpression + ", " + dayExpression + ", " + acceptedAtExpression + ", "
                        + expiresAtExpression + ")",
                intentId, fixture.buildingId(), fixture.floorId());
    }

    private void insertDemandDedupe(String schema,
                                     long floorId,
                                     String ownerExpression,
                                     UUID intentId,
                                     String dayExpression,
                                     String acceptedAtExpression) {
        jdbc.update("INSERT INTO " + table(schema, "campus_map_floor_demand_dedupe")
                        + " (owner_hmac, floor_id, utc_day, intent_id, accepted_at) VALUES ("
                        + ownerExpression + ", ?, " + dayExpression + ", ?, " + acceptedAtExpression + ")",
                floorId, intentId);
    }

    private static void assertSqlFailure(String expectedSqlState, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DataAccessException.class)
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo(expectedSqlState));
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

    private static String sqlState(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
        }
        return null;
    }

    private static byte[] hexBytes(String hexByte) {
        byte value = (byte) Integer.parseInt(hexByte, 16);
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private Long insertSubject(String schema, String name) {
        return jdbc.queryForObject("INSERT INTO " + table(schema, "subjects")
                        + " (name, type, group_id) VALUES (?, 'lecture', 1) RETURNING id",
                Long.class, name);
    }

    private int historyCount(String schema, String version) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table(schema, "flyway_schema_history")
                + " WHERE version = ?", Integer.class, version);
    }

    private int tableCount(String schema, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables"
                        + " WHERE table_schema = ? AND table_name = ?", Integer.class, schema, table);
    }

    private int columnCount(String schema, String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
                Integer.class, schema, table, column);
    }

    private void installPreFixDeferredChildOnlyTrigger(String schema) {
        String subjectTypes = table(schema, "subject_lesson_types");
        String subjects = table(schema, "subjects");
        jdbc.execute("DROP TRIGGER subject_lesson_types_minimum_trg ON " + subjectTypes);
        jdbc.execute("DROP TRIGGER subjects_lesson_type_minimum_trg ON " + subjects);
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION %s.require_subject_lesson_type()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS $fn$
                DECLARE
                    affected_subject_id BIGINT;
                BEGIN
                    affected_subject_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.subject_id ELSE NEW.subject_id END;
                    IF NOT EXISTS (
                        SELECT 1 FROM %s
                        WHERE subject_id = affected_subject_id
                    ) THEN
                        RAISE EXCEPTION 'subject %% must retain at least one lesson type', affected_subject_id;
                    END IF;
                    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
                END
                $fn$;
                """.formatted(quote(schema), subjectTypes));
        jdbc.execute("""
                CREATE CONSTRAINT TRIGGER subject_lesson_types_minimum_trg
                AFTER INSERT OR UPDATE OR DELETE ON %s
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW
                EXECUTE FUNCTION %s.require_subject_lesson_type()
                """.formatted(subjectTypes, quote(schema)));
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION %s.wait_for_pre_fix_barrier()
                RETURNS TRIGGER
                LANGUAGE plpgsql
                AS $fn$
                BEGIN
                    PERFORM pg_advisory_xact_lock_shared(%d, %d);
                    RETURN OLD;
                END
                $fn$;
                """.formatted(quote(schema), PRE_FIX_BARRIER_KEY_CLASS, PRE_FIX_BARRIER_KEY_OBJECT));
        // PostgreSQL fires same-kind triggers in name order. The lexical z
        // suffix keeps this deferred gate after the unsafe EXISTS check.
        jdbc.execute("""
                CREATE CONSTRAINT TRIGGER subject_lesson_types_z_pre_fix_barrier_trg
                AFTER DELETE ON %s
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW
                EXECUTE FUNCTION %s.wait_for_pre_fix_barrier()
                """.formatted(subjectTypes, quote(schema)));
    }

    private RaceResult raceDeletingDifferentTypes(String schema, Long subjectId, boolean preFixMode)
            throws Exception {
        Connection first = dataSource.getConnection();
        Connection second = dataSource.getConnection();
        try (first; second; Connection coordinator = preFixMode ? dataSource.getConnection() : null) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            boolean barrierHeld = preFixMode;
            if (preFixMode) {
                acquirePreFixBarrier(coordinator);
            }
            CountDownLatch deletesReady = new CountDownLatch(2);
            CountDownLatch commitGo = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            Future<CommitResult> firstCommit = executor.submit(
                    () -> deleteAndCommit(first, schema, subjectId, "lecture", deletesReady, commitGo));
            Future<CommitResult> secondCommit = executor.submit(
                    () -> deleteAndCommit(second, schema, subjectId, "practice", deletesReady, commitGo));
            try {
                assertThat(deletesReady.await(20, TimeUnit.SECONDS)).isTrue();
                commitGo.countDown();
                if (preFixMode) {
                    awaitPreFixBarrierWaiters();
                    releasePreFixBarrier(coordinator);
                    barrierHeld = false;
                }
                CommitResult firstResult = firstCommit.get(20, TimeUnit.SECONDS);
                CommitResult secondResult = secondCommit.get(20, TimeUnit.SECONDS);
                return new RaceResult(firstResult, secondResult);
            } finally {
                commitGo.countDown();
                if (barrierHeld) {
                    releasePreFixBarrier(coordinator);
                }
                executor.shutdownNow();
            }
        }
    }

    private void awaitPreFixBarrierWaiters() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        int waiting;
        do {
            waiting = jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT pid)
                    FROM pg_locks
                    WHERE locktype = 'advisory'
                      AND classid = ?
                      AND objid = ?
                      AND granted = FALSE
                    """, Integer.class, PRE_FIX_BARRIER_KEY_CLASS, PRE_FIX_BARRIER_KEY_OBJECT);
            if (waiting == 2) {
                return;
            }
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Timed out waiting for two pre-fix advisory lock waiters; observed "
                + waiting);
    }

    private static void acquirePreFixBarrier(Connection coordinator) {
        try (var statement = coordinator.prepareStatement("SELECT pg_advisory_lock(?, ?)")) {
            statement.setInt(1, PRE_FIX_BARRIER_KEY_CLASS);
            statement.setInt(2, PRE_FIX_BARRIER_KEY_OBJECT);
            statement.execute();
        } catch (SQLException exception) {
            throw new AssertionError("Could not acquire pre-fix advisory barrier", exception);
        }
    }

    private static void releasePreFixBarrier(Connection coordinator) {
        try (var statement = coordinator.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, PRE_FIX_BARRIER_KEY_CLASS);
            statement.setInt(2, PRE_FIX_BARRIER_KEY_OBJECT);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new AssertionError("Pre-fix advisory barrier was not held by coordinator");
                }
            }
        } catch (SQLException exception) {
            throw new AssertionError("Could not release pre-fix advisory barrier", exception);
        }
    }

    private CommitResult deleteAndCommit(Connection connection,
                                         String schema,
                                         Long subjectId,
                                         String lessonType,
                                         CountDownLatch deletesReady,
                                         CountDownLatch commitGo) {
        try (connection) {
            try {
                try (var statement = connection.prepareStatement("DELETE FROM "
                        + table(schema, "subject_lesson_types")
                        + " WHERE subject_id = ? AND lesson_type = ?")) {
                    statement.setLong(1, subjectId);
                    statement.setString(2, lessonType);
                    statement.executeUpdate();
                }
                deletesReady.countDown();
                if (!commitGo.await(20, TimeUnit.SECONDS)) {
                    AssertionError timeout = new AssertionError("Timed out waiting to commit race transaction");
                    rollbackOrFail(connection, timeout);
                    throw timeout;
                }
                connection.commit();
                return CommitResult.success();
            } catch (SQLException exception) {
                rollbackOrFail(connection, exception);
                String expectedSqlState = expectedRaceSqlState(exception);
                if (expectedSqlState == null) {
                    throw new AssertionError("Unexpected race failure SQLSTATE "
                            + exception.getSQLState() + ": " + exception.getMessage(), exception);
                }
                return CommitResult.expectedFailure(expectedSqlState);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                rollbackOrFail(connection, exception);
                throw new AssertionError("Race synchronization interrupted", exception);
            }
        } catch (SQLException closeFailure) {
            throw new AssertionError("Race connection close failed", closeFailure);
        }
    }

    private static void rollbackOrFail(Connection connection, Throwable cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
            throw new AssertionError("Race transaction rollback failed", cause);
        }
    }

    private static String expectedRaceSqlState(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            String sqlState = current.getSQLState();
            if (("P0001".equals(sqlState)
                    && current.getMessage() != null
                    && current.getMessage().contains("must retain at least one lesson type"))
                    || "40001".equals(sqlState)) {
                return sqlState;
            }
        }
        return null;
    }

    private static String table(String schema, String table) {
        return quote(schema) + "." + quote(table);
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private record CommitResult(boolean committed, String failureSqlState) {
        private static CommitResult success() {
            return new CommitResult(true, null);
        }

        private static CommitResult expectedFailure(String sqlState) {
            return new CommitResult(false, sqlState);
        }

        private boolean isExpectedFailure() {
            return !committed && failureSqlState != null;
        }
    }

    private record RaceResult(CommitResult first, CommitResult second) {
        private int commits() {
            return (first.committed() ? 1 : 0) + (second.committed() ? 1 : 0);
        }

        private int expectedFailures() {
            return (first.isExpectedFailure() ? 1 : 0)
                    + (second.isExpectedFailure() ? 1 : 0);
        }
    }

    private record CampusMapFixture(long buildingId, long floorId, long planVersionId) {
    }
}
