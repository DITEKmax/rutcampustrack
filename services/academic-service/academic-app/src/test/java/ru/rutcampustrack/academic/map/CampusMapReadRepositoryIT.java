package ru.rutcampustrack.academic.map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.NOT_FOUND;
import static ru.rutcampustrack.academic.map.CampusMapReadException.Code.UNAVAILABLE;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Asset;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.FormatSlot;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Manifest;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestBuilding;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestFloor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestPlan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Plan;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.PlanResult;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.ManifestResult;

/**
 * Real PostgreSQL proof for the campus-map JDBC read path.
 *
 * <p>The test-local transaction proxy deliberately consumes the production
 * {@code @Transactional} annotations.  The graph is written through JDBC in
 * each fresh schema, while a small delegating JDBC observer records the actual
 * query and BYTEA materialization order without replacing any rows.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class CampusMapReadRepositoryIT {

    private static final UUID SESSION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final long USER_ID = 100L;
    private static final long GROUP_ID = 10L;
    private static final long R1_REVISION = 101L;
    private static final long R2_REVISION = 202L;
    private static final String R1_PUBLISHED_AT = "2026-01-01 00:00:00+00";
    private static final String R1_CHANGED_PUBLISHED_AT = "2026-01-01 00:01:00+00";
    private static final String R2_PUBLISHED_AT = "2026-02-01 00:00:00+00";
    private static final AtomicInteger SCHEMA_SEQUENCE = new AtomicInteger();

    private static final byte[] RETAINED_PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x01
    };
    private static final byte[] RETAINED_SVG = "<svg viewBox=\"0 0 200 400\"/>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] R2_PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x02
    };

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rct_map_m3")
            .withUsername("rct_user")
            .withPassword("rct_dev_pass");

    private JdbcTemplate adminJdbc;

    @BeforeAll
    void initializeAdminConnection() {
        POSTGRES.start();
        adminJdbc = new JdbcTemplate(dataSourceForSchema(null));
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void realJdbcReadsOrderedGraphHistoricalAssetAndMaterializationTrace() {
        String schema = newSchema();
        migrate(schema);
        GraphFixture graph = insertGraph(schema);
        JdbcObservation observation = new JdbcObservation();
        ReadHarness harness = readHarness(schema, observation);

        ManifestResult full = harness.service().readManifest(0L, claims());

        assertThat(full.unchanged()).isFalse();
        assertThat(full.revision()).isEqualTo(R1_REVISION);
        Manifest manifest = full.manifest();
        assertThat(manifest.schemaVersion()).isEqualTo(3);
        assertThat(manifest.validationPolicyVersion()).isEqualTo(7);
        assertThat(manifest.buildings()).extracting(ManifestBuilding::id)
                .containsExactly(graph.earlyBuildingId(), graph.lateBuildingId());
        ManifestBuilding early = manifest.buildings().get(0);
        assertThat(early.floors()).extracting(ManifestFloor::id)
                .containsExactly(graph.changedFloorId(), graph.noPlanFloorId());
        assertThat(early.floors().get(0).plan().version()).isEqualTo(102L);
        assertThat(early.floors().get(0).plan().label()).isEqualTo("R1 changed draft");
        assertThat(early.floors().get(0).plan().png().state())
                .isEqualTo(CampusMapFormatState.ABSENT);
        assertThat(early.floors().get(0).plan().svg().state())
                .isEqualTo(CampusMapFormatState.PROCESSING);
        assertThat(early.floors().get(1).plan()).isNull();

        ManifestPlan retained = manifest.buildings().get(1).floors().get(0).plan();
        assertThat(retained.version()).isEqualTo(101L);
        assertThat(retained.label()).isEqualTo("R1 retained");
        assertThat(retained.png().state()).isEqualTo(CampusMapFormatState.READY);
        assertThat(retained.png().assetId()).isEqualTo(graph.retainedPngAssetId());
        assertThat(retained.png().contentType()).isEqualTo("image/png");
        assertThat(retained.png().bytes()).isEqualTo((long) RETAINED_PNG.length);
        assertThat(retained.png().sha256()).containsExactly(sha256(RETAINED_PNG));
        assertThat(retained.png().width()).isEqualTo(640);
        assertThat(retained.png().height()).isEqualTo(480);
        assertThat(retained.png().viewBox()).containsExactly(0.0, 0.0, 100.0, 200.0);
        assertThat(retained.svg().state()).isEqualTo(CampusMapFormatState.READY);
        assertThat(retained.svg().assetId()).isEqualTo(graph.retainedSvgAssetId());
        assertThat(retained.svg().contentType()).isEqualTo("image/svg+xml");
        assertThat(retained.svg().bytes()).isEqualTo((long) RETAINED_SVG.length);
        assertThat(retained.svg().width()).isEqualTo(800);
        assertThat(retained.svg().height()).isEqualTo(600);
        assertThat(retained.svg().viewBox()).containsExactly(0.0, 0.0, 200.0, 400.0);

        observation.clear();
        ManifestResult unchanged = harness.service().readManifest(R1_REVISION, claims());
        assertThat(unchanged.unchanged()).isTrue();
        assertThat(unchanged.manifest()).isNull();
        assertThat(observation.queries()).noneMatch(query ->
                query.contains("from campus_map_building")
                        || query.contains("from campus_map_floor")
                        || query.contains("from campus_map_plan_version"));

        CampusMapReadRepository directRepository =
                new CampusMapReadRepository(new JdbcTemplate(dataSourceForSchema(schema)));
        Plan physicalPlan = directRepository.findPlan(graph.retainedFloorId(), 101L).orElseThrow();
        assertThat(physicalPlan.id()).isNotEqualTo(physicalPlan.version());
        assertThat(physicalPlan.catalogRevisionId()).isEqualTo(graph.r1CatalogId());
        assertThat(physicalPlan.publishedAt())
                .isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        PlanResult currentPlan = harness.service().readFloorPlan(
                Long.toString(graph.lateBuildingId()), Long.toString(graph.retainedFloorId()), claims());
        assertThat(currentPlan.hasPlan()).isTrue();
        assertThat(currentPlan.plan().version()).isEqualTo(101L);

        observation.clear();
        Optional<Asset> asset = harness.service().readAsset(
                Long.toString(graph.lateBuildingId()), Long.toString(graph.retainedFloorId()), 101L,
                CampusMapFormat.PNG, Long.toString(graph.retainedPngAssetId()), claims(), () -> false);

        assertThat(asset).isPresent();
        assertThat(asset.orElseThrow().content()).containsExactly(RETAINED_PNG);
        int metadataQuery = observation.indexOf(JdbcObservation::isAssetMetadataQuery);
        int contentQuery = observation.indexOf(JdbcObservation::isAssetContentQuery);
        assertThat(metadataQuery).isGreaterThanOrEqualTo(0);
        assertThat(contentQuery).isGreaterThan(metadataQuery);
        assertThat(observation.queryAt(contentQuery)).contains("octet_length(content) <= ?");
        int metadataEvent = observation.eventIndex(JdbcObservation::isAssetMetadataQueryEvent);
        int contentEvent = observation.eventIndex(JdbcObservation::isAssetContentQueryEvent);
        int metadataRow = observation.eventIndex(JdbcObservation::isAssetMetadataRow);
        int contentRead = observation.eventIndex(JdbcObservation::isContentByteaRead);
        assertThat(metadataEvent).isGreaterThanOrEqualTo(0);
        assertThat(metadataRow).isGreaterThan(metadataEvent);
        assertThat(contentEvent).isGreaterThan(metadataRow);
        assertThat(observation.contentReads()).hasSize(1);
        assertThat(contentRead).isGreaterThan(contentEvent);
        assertThat(observation.contentReads().get(0)).containsExactly(RETAINED_PNG);
    }

    @Test
    void adminLifecycleDeletesAllVersionsAndUsageWithBoundReplayAndPreservesAnotherFloor() {
        String schema = newSchema();
        migrateActivity(schema);
        var admin = adminHarness(schema);
        var building = admin.createBuilding(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest("20", "Корпус"));
        var floor = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(building.id(), "1", "Первый"));
        var other = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(building.id(), "2", "Второй"));
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var svg = new org.springframework.mock.web.MockMultipartFile("svg", "map.svg", "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"/>".getBytes(StandardCharsets.UTF_8));
        admin.uploadVersion(floor.id(), null, png, svg);
        var second = admin.uploadVersion(floor.id(), "Вторая", png, null);
        var retained = admin.uploadVersion(other.id(), null, png, svg);
        assertThat(second.version()).isEqualTo("2");
        assertThat(second.svg().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FormatState.ready);
        admin.updateBuilding(building.id(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("21", "Новый корпус"));
        var updated = admin.updateFloor(floor.id(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("3", "Новый этаж"));
        assertThat(updated.id()).isEqualTo(floor.id());
        assertThat(updated.currentPlan().version()).isEqualTo("2");
        assertThatThrownBy(() -> admin.updateFloor(floor.id(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("2", "Занят")))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);

        long floorId = Long.parseLong(floor.id());
        long buildingId = Long.parseLong(building.id());
        var usage = new CampusMapUsageRepository(jdbc(schema));
        UUID intent = UUID.randomUUID();
        usage.insertOpenIntent(new byte[32], intent, buildingId, floorId, new byte[32]);
        var open = usage.findOpenIntentForUpdate(new byte[32], intent).orElseThrow();
        usage.insertDemandDedupe(new byte[32], floorId, open.acceptedUtcDay(), intent, open.acceptedAt());
        usage.insertDemandDedupe(new byte[32], floorId, open.acceptedUtcDay(), intent, open.acceptedAt());
        assertThat(jdbc(schema).queryForObject("SELECT SUM(open_count) FROM campus_map_floor_hourly_activity WHERE floor_id = ?", Long.class, floorId)).isEqualTo(1L);
        var deletion = deletionHarness(schema, CampusMapDeletionRepository::new);
        org.springframework.test.util.ReflectionTestUtils.setField(deletion, "adminAccess", activityAdminAuthority(jdbc(schema)));
        var preview = deletion.preview(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.FLOOR, floorId);
        assertThat(preview.versions()).isEqualTo(2);
        assertThat(preview.assets()).isEqualTo(4);
        assertThat(preview.openCount()).isEqualTo(1);
        assertThat(preview.remainingFloors()).isEqualTo(1);
        var request = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(UUID.randomUUID(), preview.previewDigest(), "password");
        assertThat(deletion.delete(preview.targetType(), floorId, USER_ID, request).status()).isEqualTo("COMPLETED");
        assertThat(deletion.delete(preview.targetType(), floorId, USER_ID, request).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> deletion.delete(preview.targetType(), floorId, USER_ID + 1, request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        for (String table : List.of("campus_map_floor", "campus_map_plan_version", "campus_map_asset",
                "campus_map_plan_format", "campus_map_open_intent", "campus_map_floor_demand_dedupe", "campus_map_floor_daily_demand", "campus_map_floor_hourly_activity")) {
            String scope = switch (table) {
                case "campus_map_floor" -> "id = " + floorId;
                case "campus_map_asset", "campus_map_plan_format" -> "plan_version_id NOT IN (SELECT id FROM campus_map_plan_version)";
                default -> "floor_id = " + floorId;
            };
            assertThat(jdbc(schema).queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + scope, Long.class)).isZero();
        }
        assertThat(admin.getVersion(other.id(), retained.version()).png().id()).isEqualTo(retained.png().id());
        assertThat(admin.downloadAsset(other.id(), retained.version(), "png", retained.png().id()).content()).containsExactly(uploadPng());
        assertThatThrownBy(() -> deletion.preview(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.BUILDING, buildingId))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        var otherPreview = deletion.preview(preview.targetType(), Long.parseLong(other.id()));
        deletion.delete(otherPreview.targetType(), Long.parseLong(other.id()), USER_ID,
                new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(UUID.randomUUID(), otherPreview.previewDigest(), "password"));
        var empty = deletion.preview(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.BUILDING, buildingId);
        deletion.delete(empty.targetType(), buildingId, USER_ID,
                new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(UUID.randomUUID(), empty.previewDigest(), "password"));
        assertThat(admin.listBuildings()).isEmpty();
    }

    @Test void activityUsesExactMoscowCutoffsAndReportsUnknownHistoryUntilDurableCoverage() {
        String schema = newSchema(); migrateActivity(schema);
        GraphFixture graph = insertGraph(schema);
        JdbcTemplate db = jdbc(schema);
        var now = db.queryForObject("SELECT CURRENT_TIMESTAMP", OffsetDateTime.class).toInstant();
        var clock = java.time.Clock.fixed(now, java.time.ZoneOffset.UTC);
        var window = ru.rutcampustrack.academic.dashboard.ActivityWindow.of("today", "day", clock);
        // Accepted map operations can outlive the target date. Count the actual
        // timestamps, including the exact exclusive endpoint of yesterday.
        activityOpen(db, graph.lateBuildingId(), graph.retainedFloorId(), window.summary().from().plusSeconds(1));
        activityOpen(db, graph.lateBuildingId(), graph.retainedFloorId(), window.previous().from().plusSeconds(1));
        activityOpen(db, graph.lateBuildingId(), graph.retainedFloorId(), window.previous().to());
        var repository = transactionProxy(new ru.rutcampustrack.academic.dashboard.DashboardActivityReadRepository(db), dataSourceForSchema(schema));
        var counts = repository.read(window);
        assertThat(counts.summary().count()).isEqualTo(1);
        assertThat(counts.previous().count()).isEqualTo(1); // endpoint itself is excluded
        assertThat(counts.series().getFirst().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.State.INSUFFICIENT_HISTORY);
        assertThat(counts.series().getFirst().count()).isNull();
        assertThat(counts.series().getLast().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.State.AVAILABLE);
        var allTime = repository.read(ru.rutcampustrack.academic.dashboard.ActivityWindow.of("all_time", "day", clock));
        assertThat(allTime.summary().count()).isEqualTo(3);
        // The fixture deliberately supplies a known capture boundary and a
        // complete historic hour. It never reconstructs Moscow from UTC days.
        db.update("UPDATE campus_map_activity_coverage SET captured_from = ?", OffsetDateTime.ofInstant(window.series().getFirst().from(), java.time.ZoneOffset.UTC));
        db.update("INSERT INTO campus_map_floor_hourly_activity(floor_id,hour_start,open_count) VALUES (?,?,?)",
                graph.retainedFloorId(), OffsetDateTime.ofInstant(window.series().getFirst().from(), java.time.ZoneOffset.UTC), 7L);
        counts = repository.read(window);
        assertThat(counts.series().getFirst().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.dashboard.DashboardActivityResponse.State.AVAILABLE);
        assertThat(counts.series().getFirst().count()).isEqualTo(7);
    }

    private void activityOpen(JdbcTemplate db, long building, long floor, java.time.Instant accepted) {
        UUID intent = UUID.randomUUID();
        OffsetDateTime at = OffsetDateTime.ofInstant(accepted, java.time.ZoneOffset.UTC);
        db.update("""
                INSERT INTO campus_map_open_intent(owner_hmac,intent_id,building_id,floor_id,payload_hash,accepted_utc_day,accepted_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, new byte[32], intent, building, floor, new byte[32], at.toLocalDate(), at, at.plusHours(48));
        var usage = new CampusMapUsageRepository(db);
        usage.insertDemandDedupe(new byte[32], floor, at.toLocalDate(), intent, at);
        usage.insertDemandDedupe(new byte[32], floor, at.toLocalDate(), intent, at);
    }

    private void migrateActivity(String schema) {
        Flyway.configure().dataSource(dataSourceForSchema(schema)).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema).cleanDisabled(true).target("54").load().migrate();
    }

    @Test
    void planTargetClearPreservesHistoryUsageAndReplaysAgainstOriginalIdentity() {
        String schema = newSchema();
        migrateActivity(schema);
        var db = jdbc(schema);
        var source = dataSourceForSchema(schema);
        var admin = transactionProxy(new CampusMapAdminService(new CampusMapAdminRepository(db),
                new CampusMapUsageService(null, new CampusMapUsageRepository(db), null, event -> {})), source);
        long buildingsBefore = db.queryForObject("SELECT COUNT(*) FROM campus_map_building", Long.class);
        assertThat(admin.getPlanTarget("91", "2").status().name()).isEqualTo("absent");
        assertThat(admin.getPlanTarget("91", "2").floor()).isNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_map_building", Long.class)).isEqualTo(buildingsBefore);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var svg = new org.springframework.mock.web.MockMultipartFile("svg", "map.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8));
        var original = admin.uploadPlan("91", "2", "План", png, svg).plan();
        long floorId = Long.parseLong(original.floorId());
        db.update("INSERT INTO campus_map_floor_daily_demand(floor_id,utc_day,open_count) VALUES (?,CURRENT_DATE,7)", floorId);
        assertThat(admin.getPlanTarget("91", "2").floor().openCount()).isEqualTo(7L);
        var deletion = deletionHarness(schema, CampusMapDeletionRepository::new);
        org.springframework.test.util.ReflectionTestUtils.setField(deletion, "adminAccess", activityAdminAuthority(db));
        var preview = deletion.previewPlan("91", "2", ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.Format.png);
        assertThat(preview.files()).isEqualTo(1L);
        var request = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanDeleteRequest(
                "91", "2", preview.format(), UUID.randomUUID(), preview.previewDigest(), "synthetic");
        long confirmed = deletion.planConfirmationTarget(USER_ID, request);
        db.update("UPDATE campus_map_floor_daily_demand SET open_count=8 WHERE floor_id=?", floorId);
        assertThat(deletion.deletePlan(confirmed, USER_ID, request).status()).isEqualTo("COMPLETED");
        var successor = admin.getPlanTarget("91", "2").floor();
        assertThat(successor.currentPlan().png().state().name()).isEqualTo("absent");
        assertThat(successor.currentPlan().svg().state().name()).isEqualTo("ready");
        assertThat(successor.openCount()).isEqualTo(8L);
        assertThat(admin.downloadAsset(original.floorId(), original.version(), "png", original.png().id()).content())
                .containsExactly(uploadPng());
        assertThat(deletion.deletePlan(confirmed, USER_ID, request).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> deletion.delete(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.FLOOR,
                confirmed, USER_ID, new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(
                        request.operationId(), request.previewDigest(), "synthetic")))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        var allPreview = deletion.previewPlan("91", "2", null);
        var clear = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanDeleteRequest(
                "91", "2", null, UUID.randomUUID(), allPreview.previewDigest(), "synthetic");
        assertThat(deletion.deletePlan(deletion.planConfirmationTarget(USER_ID, clear), USER_ID, clear).status()).isEqualTo("COMPLETED");
        assertThat(admin.getPlanTarget("91", "2").floor().currentPlan()).isNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_map_plan_version WHERE floor_id=?", Long.class, floorId)).isEqualTo(2L);
        var replaced = admin.uploadPlan("91", "2", "После снятия", png, null).plan();
        assertThat(replaced.version()).isEqualTo("3");
        assertThat(deletion.deletePlan(confirmed, USER_ID, clear).status()).isEqualTo("COMPLETED");
        assertThat(admin.getPlanTarget("91", "2").floor().currentPlan().version()).isEqualTo("3");
        admin.updateFloor(original.floorId(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("3", "Переименован"));
        var reused = admin.uploadPlan("91", "2", null, png, null).plan();
        assertThat(reused.floorId()).isNotEqualTo(original.floorId());
        assertThat(deletion.planConfirmationTarget(USER_ID, clear)).isEqualTo(confirmed);
        assertThat(deletion.deletePlan(confirmed, USER_ID, clear).status()).isEqualTo("COMPLETED");
        assertThat(admin.getPlanTarget("91", "2").floor().currentPlan()).isNotNull();
        assertThatThrownBy(() -> deletion.planConfirmationTarget(USER_ID + 1, clear))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        assertThatThrownBy(() -> deletion.planConfirmationTarget(USER_ID, new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanDeleteRequest(
                "91", "3", null, clear.operationId(), clear.previewDigest(), "synthetic")))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
    }

    @Test
    void planTargetStalePreviewAndReceiptFailureKeepPointerAndHistory() {
        String schema = newSchema();
        migrateActivity(schema);
        var db = jdbc(schema);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var original = admin.uploadPlan("92", "1", null, png, null).plan();
        var authority = activityAdminAuthority(db);
        var deletion = deletionHarness(schema, CampusMapDeletionRepository::new);
        org.springframework.test.util.ReflectionTestUtils.setField(deletion, "adminAccess", authority);
        var preview = deletion.previewPlan("92", "1", null);
        var stale = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanDeleteRequest(
                "92", "1", null, UUID.randomUUID(), preview.previewDigest(), "synthetic");
        long floor = Long.parseLong(original.floorId());
        // The Auth target was resolved before a concurrent replacement.
        long confirmed = deletion.planConfirmationTarget(USER_ID, stale);
        admin.uploadPlan("92", "1", null, png, null);
        assertThatThrownBy(() -> deletion.deletePlan(confirmed, USER_ID, stale))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        assertThat(admin.getPlanTarget("92", "1").floor().currentPlan().version()).isEqualTo("2");
        var refreshed = deletion.previewPlan("92", "1", null);
        var request = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.PlanDeleteRequest(
                "92", "1", null, UUID.randomUUID(), refreshed.previewDigest(), "synthetic");
        var fault = deletionHarness(schema, jdbc -> new CampusMapDeletionRepository(jdbc) {
            @Override public void insertPlanReceipt(UUID operationId, long ownerId, long floorId,
                                                   String digest, String buildingCode, String floorCode, String format) {
                throw new IllegalStateException("synthetic receipt failure");
            }
        });
        org.springframework.test.util.ReflectionTestUtils.setField(fault, "adminAccess", authority);
        assertThatThrownBy(() -> fault.deletePlan(floor, USER_ID, request)).isInstanceOf(IllegalStateException.class);
        assertThat(admin.getPlanTarget("92", "1").floor().currentPlan().version()).isEqualTo("2");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_map_deletion_receipt WHERE operation_id=?", Long.class, request.operationId())).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM campus_map_plan_version WHERE floor_id=?", Long.class, floor)).isEqualTo(2L);
        // Renumbering away and back still invalidates the original preview.
        admin.updateFloor(original.floorId(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("2", "Второй"));
        admin.updateFloor(original.floorId(), new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UpdateInventoryRequest("1", "Первый"));
        assertThatThrownBy(() -> deletion.deletePlan(floor, USER_ID, request))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
    }

    private ru.rutcampustrack.academic.admin.AcademicAdminAccess activityAdminAuthority(JdbcTemplate db) {
        // The existing constructor-only deletion harness predates the current
        // ADMIN authority. Keep the real guard and give this V52 scenario a
        // persisted synthetic FULL grant and live selected-role session.
        if (db.queryForObject("SELECT count(*) FROM admin_access_profiles WHERE user_id=?", Long.class, USER_ID) == 0) {
        db.update("INSERT INTO users(id,login,last_name,first_name) VALUES (?, 'activity-admin', 'Synthetic', 'Admin')", USER_ID);
        db.update("INSERT INTO admin_access_profiles(user_id,assigned,mode,version) VALUES (?,TRUE,'FULL',1)", USER_ID);
        Long grant = db.queryForObject("""
                INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at)
                VALUES (?,'admin','active',NOW(),NOW()) RETURNING id
                """, Long.class, USER_ID);
        db.update("""
                INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,
                    refresh_expires_at,created_at,last_seen_at,auth_method)
                VALUES (?,?,?,1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')
                """, SESSION_ID, USER_ID, grant, UUID.randomUUID());
        }
        var context = new ru.rutcampustrack.academic.security.RequestContext();
        context.setUserId(USER_ID);
        context.setRole(ru.rutcampustrack.academic.contract.enums.UserRole.ADMIN);
        context.setClaims(new InternalJwtClaims(USER_ID, SESSION_ID, 1,
                db.queryForObject("SELECT roles_version FROM users WHERE id=?", Long.class, USER_ID),
                "ADMIN", "ACTIVE", null, false, false));
        return new ru.rutcampustrack.academic.admin.AcademicAdminAccess(
                new ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority(db), context, db);
    }

    @Test
    void stalePreviewAndMidDeleteFailureLeavePublishedPlanAndBytesIntact() {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var building = admin.createBuilding(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest("20", null));
        var floor = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(building.id(), "1", null));
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var plan = admin.uploadVersion(floor.id(), null, png, null);
        long floorId = Long.parseLong(floor.id());
        var deletion = deletionHarness(schema, CampusMapDeletionRepository::new);
        var preview = deletion.preview(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.FLOOR, floorId);
        admin.uploadVersion(floor.id(), null, png, null);
        assertThatThrownBy(() -> deletion.delete(preview.targetType(), floorId, USER_ID,
                new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(UUID.randomUUID(), preview.previewDigest(), "password")))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        var refreshed = deletion.preview(preview.targetType(), floorId);
        UUID operation = UUID.randomUUID();
        var fault = deletionHarness(schema, jdbc -> new CampusMapDeletionRepository(jdbc) {
            @Override public void insertReceipt(UUID op, long owner, ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget type, long target, String digest) {
                throw new IllegalStateException("injected receipt failure after cleanup");
            }
        });
        var request = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(operation, refreshed.previewDigest(), "password");
        assertThatThrownBy(() -> fault.delete(preview.targetType(), floorId, USER_ID, request)).isInstanceOf(IllegalStateException.class);
        assertThat(admin.listFloors(building.id()).get(0).currentPlan().version()).isEqualTo("2");
        assertThat(admin.downloadAsset(floor.id(), "1", "png", plan.png().id()).content()).containsExactly(uploadPng());
        assertThat(jdbc(schema).queryForObject("SELECT COUNT(*) FROM campus_map_deletion_receipt", Long.class)).isZero();
        assertThatThrownBy(() -> jdbc(schema).update("DELETE FROM campus_map_asset WHERE id = ?", Long.parseLong(plan.png().id())))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(deletion.delete(preview.targetType(), floorId, USER_ID, request).status()).isEqualTo("COMPLETED");
    }

    @Test
    void concurrentReplacementSerializesBeforeFinalDeletionAndCannotResurrectFloor() throws Exception {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var building = admin.createBuilding(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest("20", null));
        var floor = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(building.id(), "1", null));
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        admin.uploadVersion(floor.id(), null, png, null);
        long floorId = Long.parseLong(floor.id());
        CountDownLatch deleting = new CountDownLatch(1), resume = new CountDownLatch(1);
        var deletion = deletionHarness(schema, jdbc -> new CampusMapDeletionRepository(jdbc) {
            @Override public void deleteFloor(long id) {
                deleting.countDown();
                try { if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("delete gate timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                super.deleteFloor(id);
            }
        });
        var preview = deletion.preview(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget.FLOOR, floorId);
        var request = new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeleteRequest(UUID.randomUUID(), preview.previewDigest(), "password");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var removed = pool.submit(() -> deletion.delete(preview.targetType(), floorId, USER_ID, request));
            assertThat(deleting.await(10, TimeUnit.SECONDS)).isTrue();
            var replacement = pool.submit(() -> admin.uploadVersion(floor.id(), null, png, null));
            awaitAdvisoryWait(schema);
            resume.countDown();
            assertThat(removed.get(10, TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
            assertThatThrownBy(() -> replacement.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException.class);
            assertThat(admin.listFloors(building.id())).isEmpty();
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test
    void concurrentPublishWaiterSeesCommittedCurrentPlanAndAdvancesVersion() throws Exception {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var building = admin.createBuilding(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateBuildingRequest("20", null));
        var floor = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(building.id(), "1", null));
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        admin.uploadVersion(floor.id(), null, png, null);
        CountDownLatch locked = new CountDownLatch(1), resume = new CountDownLatch(1);
        var source = dataSourceForSchema(schema);
        var first = transactionProxy(new CampusMapAdminService(new CampusMapAdminRepository(new JdbcTemplate(source)) {
            @Override public void lockCatalogForWrite() {
                super.lockCatalogForWrite(); locked.countDown();
                try { if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("publish gate timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
        }), source);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var upload1 = pool.submit(() -> first.uploadVersion(floor.id(), null, png, null));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var upload2 = pool.submit(() -> admin.uploadVersion(floor.id(), null, png, null));
            awaitAdvisoryWait(schema); resume.countDown();
            assertThat(upload1.get(10, TimeUnit.SECONDS).version()).isEqualTo("2");
            assertThat(upload2.get(10, TimeUnit.SECONDS).version()).isEqualTo("3");
            assertThat(admin.listFloors(building.id()).get(0).currentPlan().version()).isEqualTo("3");
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    @Test
    void oneFormUploadCreatesOrReplacesWithReadbackAndRetainedFormatsAndHistory() {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var first = admin.uploadPlan("20", "2", "Первая схема", png, null);
        assertThat(first.status()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus.created);
        assertThat(first.buildingCreated()).isTrue();
        assertThat(first.floorCreated()).isTrue();
        assertThat(first.plan().version()).isEqualTo("1");
        assertThat(first.plan().svg().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FormatState.absent);
        var svg = new org.springframework.mock.web.MockMultipartFile("svg", "map.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"100\"></svg>".getBytes(StandardCharsets.UTF_8));
        var replacement = admin.uploadPlan("20", "2", "Новая схема", null, svg);
        assertThat(replacement.status()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus.replaced);
        assertThat(replacement.buildingCreated()).isFalse();
        assertThat(replacement.floorCreated()).isFalse();
        assertThat(replacement.plan().floorId()).isEqualTo(first.plan().floorId());
        assertThat(replacement.plan().version()).isEqualTo("2");
        assertThat(replacement.plan().png().sha256()).isEqualTo(first.plan().png().sha256());
        assertThat(replacement.plan().svg().state()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.FormatState.ready);
        var buildings = admin.listBuildings();
        assertThat(buildings).hasSize(1);
        assertThat(buildings.get(0).code()).isEqualTo("20");
        assertThat(buildings.get(0).label()).isEqualTo("Корпус 20");
        assertThat(buildings.get(0).floors()).hasSize(1);
        assertThat(buildings.get(0).floors().get(0).currentPlan()).isEqualTo(replacement.plan());
        assertThat(admin.getVersion(first.plan().floorId(), "1")).isEqualTo(first.plan());
        assertThat(admin.downloadAsset(first.plan().floorId(), "1", "png", first.plan().png().id()).content())
                .isEqualTo(uploadPng());
        assertThat(admin.downloadAsset(replacement.plan().floorId(), "2", "png", replacement.plan().png().id()).content())
                .isEqualTo(uploadPng());
        var existingEmptyFloor = admin.createFloor(new ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.CreateFloorRequest(first.plan().buildingId(), "3", null));
        var existingFloorFirstPlan = admin.uploadPlan("20", "3", null, png, null);
        assertThat(existingFloorFirstPlan.status()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus.created);
        assertThat(existingFloorFirstPlan.buildingCreated()).isFalse();
        assertThat(existingFloorFirstPlan.floorCreated()).isFalse();
        assertThat(existingFloorFirstPlan.plan().floorId()).isEqualTo(existingEmptyFloor.id());
    }

    @Test
    void oneFormInvalidInputsAndFilesLeaveNoInventoryOrPublication() {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        long revisionCount = jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class);
        for (String code : List.of("0", "01", " 20", "20 ", "+20", "２０", "1".repeat(65))) {
            assertThatThrownBy(() -> admin.uploadPlan(code, "2", null, png, null))
                    .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
            assertThatThrownBy(() -> admin.uploadPlan("20", code, null, png, null))
                    .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        }
        assertThatThrownBy(() -> admin.uploadPlan("20", "2", "x".repeat(256), png, null))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        assertThatThrownBy(() -> admin.uploadPlan("20", "2", null, null, null))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        var unsafeSvg = new org.springframework.mock.web.MockMultipartFile("svg", "map.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> admin.uploadPlan("20", "2", null, png, unsafeSvg))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        var invalidPng = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> admin.uploadPlan("20", "2", null, invalidPng, null))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_building", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_floor", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_asset", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class)).isEqualTo(revisionCount);
    }

    @Test
    void oneFormFailureAfterCurrentPointerWriteRollsBackNewInventoryAndExistingHistory() {
        String schema = newSchema(); migrateDeletion(schema);
        var source = dataSourceForSchema(schema);
        var failing = transactionProxy(new CampusMapAdminService(new CampusMapAdminRepository(new JdbcTemplate(source)) {
            @Override public void pointFloorAt(long floorId, long planId) {
                super.pointFloorAt(floorId, planId);
                throw new IllegalStateException("forced publication failure");
            }
        }), source);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        long revisionCount = jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class);
        assertThatThrownBy(() -> failing.uploadPlan("20", "2", null, png, null)).isInstanceOf(IllegalStateException.class);
        assertThat(admin.listBuildings()).isEmpty();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_floor", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_asset", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_format", Long.class)).isZero();
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class)).isEqualTo(revisionCount);
        var first = admin.uploadPlan("20", "2", null, png, null);
        long retainedRevisions = jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class);
        assertThatThrownBy(() -> failing.uploadPlan("20", "2", "Не опубликована", png, null)).isInstanceOf(IllegalStateException.class);
        assertThat(admin.listFloors(first.plan().buildingId()).get(0).currentPlan()).isEqualTo(first.plan());
        assertThat(admin.getVersion(first.plan().floorId(), "1")).isEqualTo(first.plan());
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isEqualTo(1L);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_asset", Long.class)).isEqualTo(1L);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_format", Long.class)).isEqualTo(2L);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_catalog_revision", Long.class)).isEqualTo(retainedRevisions);
        assertThat(admin.uploadPlan("20", "2", null, png, null).plan().version()).isEqualTo("2");
    }

    @Test
    void oneFormInactiveCodesCannotCreateDuplicateInventoryOrReactivateIt() {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        var first = admin.uploadPlan("20", "2", null, png, null);
        jdbc(schema).update("UPDATE campus_map_floor SET is_active = FALSE WHERE id = ?", Long.parseLong(first.plan().floorId()));
        assertThatThrownBy(() -> admin.uploadPlan("20", "2", null, png, null))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        jdbc(schema).update("UPDATE campus_map_building SET is_active = FALSE WHERE id = ?", Long.parseLong(first.plan().buildingId()));
        assertThatThrownBy(() -> admin.uploadPlan("20", "3", null, png, null))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_building", Long.class)).isEqualTo(1L);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_floor", Long.class)).isEqualTo(1L);
        assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isEqualTo(1L);
    }

    @Test
    void oneFormConcurrentSameCodesProduceOneInventoryAndSequentialVersions() throws Exception {
        String schema = newSchema(); migrateDeletion(schema);
        var admin = adminHarness(schema);
        var png = new org.springframework.mock.web.MockMultipartFile("png", "map.png", "image/png", uploadPng());
        CountDownLatch locked = new CountDownLatch(1), resume = new CountDownLatch(1);
        var source = dataSourceForSchema(schema);
        var first = transactionProxy(new CampusMapAdminService(new CampusMapAdminRepository(new JdbcTemplate(source)) {
            @Override public void lockCatalogForWrite() {
                super.lockCatalogForWrite(); locked.countDown();
                try { if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("publish gate timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
        }), source);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var upload1 = pool.submit(() -> first.uploadPlan("20", "2", null, png, null));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var upload2 = pool.submit(() -> admin.uploadPlan("20", "2", null, png, null));
            awaitAdvisoryWait(schema); resume.countDown();
            var created = upload1.get(10, TimeUnit.SECONDS);
            var replaced = upload2.get(10, TimeUnit.SECONDS);
            assertThat(created.status()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus.created);
            assertThat(created.buildingCreated()).isTrue();
            assertThat(created.floorCreated()).isTrue();
            assertThat(replaced.status()).isEqualTo(ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.UploadPlanStatus.replaced);
            assertThat(replaced.buildingCreated()).isFalse();
            assertThat(replaced.floorCreated()).isFalse();
            assertThat(created.plan().floorId()).isEqualTo(replaced.plan().floorId());
            assertThat(replaced.plan().version()).isEqualTo("2");
            assertThat(admin.listFloors(created.plan().buildingId()).get(0).currentPlan()).isEqualTo(replaced.plan());
            assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_building", Long.class)).isEqualTo(1L);
            assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_floor", Long.class)).isEqualTo(1L);
            assertThat(jdbc(schema).queryForObject("SELECT count(*) FROM campus_map_plan_version", Long.class)).isEqualTo(2L);
        } finally { resume.countDown(); pool.shutdownNow(); }
    }

    private void awaitAdvisoryWait(String schema) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(jdbc(schema).queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND NOT granted)", Boolean.class))) return;
            Thread.sleep(20);
        }
        throw new AssertionError("concurrent command never waited on the catalog lock");
    }

    private void migrateDeletion(String schema) {
        Flyway.configure().dataSource(dataSourceForSchema(schema)).locations("classpath:db/migration")
                // Current deletion entry points require V51's durable ADMIN authority.
                .schemas(schema).defaultSchema(schema).cleanDisabled(true).target("51").load().migrate();
    }

    private CampusMapAdminService adminHarness(String schema) {
        var source = dataSourceForSchema(schema);
        return transactionProxy(new CampusMapAdminService(new CampusMapAdminRepository(new JdbcTemplate(source))), source);
    }

    private CampusMapDeletionTransaction deletionHarness(String schema,
            java.util.function.Function<JdbcTemplate, CampusMapDeletionRepository> factory) {
        var source = dataSourceForSchema(schema);
        var jdbc = new JdbcTemplate(source);
        var transaction = new CampusMapDeletionTransaction(new CampusMapAdminRepository(jdbc), factory.apply(jdbc));
        org.springframework.test.util.ReflectionTestUtils.setField(transaction, "adminAccess", activityAdminAuthority(jdbc));
        return transactionProxy(transaction, source);
    }

    @SuppressWarnings("unchecked")
    private <T> T transactionProxy(T target, DataSource source) {
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(new DataSourceTransactionManager(source));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(advice);
        return (T) factory.getProxy();
    }

    private static byte[] uploadPng() {
        byte[] png = new byte[24];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, png, 0, 8);
        java.nio.ByteBuffer.wrap(png).putInt(16, 640).putInt(20, 480); return png;
    }

    @Test
    void realJdbcNegativeReadsReturnTypedBoundariesWithoutContentMaterialization() {
        String schema = newSchema();
        migrate(schema);
        GraphFixture graph = insertGraph(schema);
        JdbcObservation observation = new JdbcObservation();
        ReadHarness harness = readHarness(schema, observation);

        assertMapCode(NOT_FOUND, () -> harness.service().readAsset(
                Long.toString(graph.earlyBuildingId()), Long.toString(graph.retainedFloorId()), 101L,
                CampusMapFormat.PNG, Long.toString(graph.retainedPngAssetId()), claims(), () -> false));
        assertNoContentQuery(observation);

        observation.clear();
        assertMapCode(NOT_FOUND, () -> harness.service().readAsset(
                Long.toString(graph.earlyBuildingId()), "999999", 101L, CampusMapFormat.PNG,
                Long.toString(graph.retainedPngAssetId()), claims(), () -> false));
        assertNoContentQuery(observation);

        observation.clear();
        assertMapCode(NOT_FOUND, () -> harness.service().readAsset(
                Long.toString(graph.lateBuildingId()), Long.toString(graph.retainedFloorId()), 999999L,
                CampusMapFormat.PNG, Long.toString(graph.retainedPngAssetId()), claims(), () -> false));
        assertNoContentQuery(observation);

        observation.clear();
        assertMapCode(NOT_FOUND, () -> harness.service().readAsset(
                Long.toString(graph.lateBuildingId()), Long.toString(graph.retainedFloorId()), 101L,
                CampusMapFormat.PNG, Long.toString(graph.retainedPngAssetId() + 99999L), claims(), () -> false));
        assertNoContentQuery(observation);

        observation.clear();
        PlanResult noPlan = harness.service().readFloorPlan(
                Long.toString(graph.earlyBuildingId()), Long.toString(graph.noPlanFloorId()), claims());
        assertThat(noPlan.hasPlan()).isFalse();
        assertThat(observation.queries()).noneMatch(query ->
                query.contains("from campus_map_plan_version"));

        String emptySchema = newSchema();
        migrate(emptySchema);
        JdbcObservation unavailableObservation = new JdbcObservation();
        ReadHarness unavailableHarness = readHarness(emptySchema, unavailableObservation);
        assertMapCode(UNAVAILABLE, () -> unavailableHarness.service().readManifest(0L, claims()));
        assertNoContentQuery(unavailableObservation);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ABSENT", "PROCESSING", "FAILED"})
    void realJdbcNonReadyAssetWithNullAssetIdIsNotFoundBeforeMetadataOrContent(String state) {
        String schema = newSchema();
        migrate(schema);
        NonReadyFixture fixture = insertNonReadyGraph(schema, state);
        JdbcObservation observation = new JdbcObservation();
        ReadHarness harness = readHarness(schema, observation);

        assertMapCode(NOT_FOUND, () -> harness.service().readAsset(
                Long.toString(fixture.buildingId()), Long.toString(fixture.floorId()), 501L,
                CampusMapFormat.PNG, "900", claims(), () -> false));
        assertThat(observation.queries()).noneMatch(JdbcObservation::isAssetMetadataQuery);
        assertNoContentQuery(observation);
    }

    @Test
    void repeatableReadSnapshotStaysAtR1AcrossCommittedCatalogSwitch() throws Exception {
        String schema = newSchema();
        migrate(schema);
        GraphFixture graph = insertGraph(schema);
        CatalogGate gate = new CatalogGate();
        JdbcObservation observation = new JdbcObservation(gate);
        ReadHarness harness = readHarness(schema, observation);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<ManifestResult> firstRead = executor.submit(() ->
                harness.service().readManifest(0L, claims()));
        Connection writer = null;
        boolean writerCommitted = false;
        try {
            assertThat(gate.catalogReturned.await(10, TimeUnit.SECONDS))
                    .as("reader must return the real first catalog query before the switch")
                    .isTrue();

            writer = dataSourceForSchema(schema).getConnection();
            writer.setAutoCommit(false);
            switchCurrentCatalogAndFloor(writer, graph);
            writer.commit();
            writerCommitted = true;
            gate.resume.countDown();

            ManifestResult first = firstRead.get(20, TimeUnit.SECONDS);
            assertThat(first.revision()).isEqualTo(R1_REVISION);
            assertThat(first.manifest().buildings().get(0).floors().get(0).plan().version())
                    .isEqualTo(102L);
            assertThat(first.manifest().buildings().get(1).floors().get(0).plan().version())
                    .isEqualTo(101L);
            assertThat(first.manifest().buildings().get(0).floors().get(1).plan()).isNull();

            JdbcTemplate afterSwitchJdbc = new JdbcTemplate(dataSourceForSchema(schema));
            assertThat(afterSwitchJdbc.queryForObject(
                    "SELECT revision FROM campus_map_catalog_revision WHERE is_current = TRUE",
                    Long.class)).isEqualTo(R2_REVISION);
            assertThat(afterSwitchJdbc.queryForObject(
                    "SELECT current_version_id FROM campus_map_floor WHERE id = ?",
                    Long.class, graph.changedFloorId())).isEqualTo(graph.changedR2PlanId());

            ManifestResult second = harness.service().readManifest(0L, claims());
            assertThat(second.revision()).isEqualTo(R2_REVISION);
            assertThat(second.manifest().buildings().get(0).floors().get(0).plan().version())
                    .isEqualTo(202L);
            assertThat(second.manifest().buildings().get(1).floors().get(0).plan().version())
                    .isEqualTo(101L);
            assertThat(second.manifest().buildings().get(0).floors().get(1).plan()).isNull();
        } finally {
            gate.resume.countDown();
            try {
                if (writer != null && !writerCommitted) {
                    writer.rollback();
                }
            } finally {
                try {
                    if (writer != null) {
                        writer.close();
                    }
                } finally {
                    executor.shutdownNow();
                    assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                            .as("snapshot reader executor must stop")
                            .isTrue();
                }
            }
        }
    }

    private GraphFixture insertGraph(String schema) {
        JdbcTemplate jdbc = jdbc(schema);
        long r1CatalogId = insertCatalog(jdbc, R1_REVISION, 3, 7, true);
        long r2CatalogId = insertCatalog(jdbc, R2_REVISION, 3, 7, false);

        long lateBuildingId = insertBuilding(jdbc, "B-LATE", "Late building", 20);
        long earlyBuildingId = insertBuilding(jdbc, "B-EARLY", "Early building", 10);
        long noPlanFloorId = insertFloor(jdbc, earlyBuildingId, "NP", "No plan", 2);
        long changedFloorId = insertFloor(jdbc, earlyBuildingId, "CH", "Changed floor", 1);
        long retainedFloorId = insertFloor(jdbc, lateBuildingId, "RET", "Retained floor", 8);

        long retainedR1PlanId = insertPlan(jdbc, retainedFloorId, 101L, r1CatalogId, "R1 retained");
        long changedR1PlanId = insertPlan(jdbc, changedFloorId, 102L, r1CatalogId, "R1 changed draft");
        long changedR2PlanId = insertPlan(jdbc, changedFloorId, 202L, r2CatalogId, "R2 changed current");

        long retainedPngAssetId = insertAsset(jdbc, retainedR1PlanId, "PNG", "image/png",
                RETAINED_PNG, 640, 480);
        long retainedSvgAssetId = insertAsset(jdbc, retainedR1PlanId, "SVG", "image/svg+xml",
                RETAINED_SVG, 800, 600);
        long changedR2PngAssetId = insertAsset(jdbc, changedR2PlanId, "PNG", "image/png",
                R2_PNG, 640, 480);

        insertFormat(jdbc, retainedR1PlanId, "PNG", "READY", "image/png", retainedPngAssetId,
                RETAINED_PNG, 640, 480, new double[]{0.0, 0.0, 100.0, 200.0});
        insertFormat(jdbc, retainedR1PlanId, "SVG", "READY", "image/svg+xml", retainedSvgAssetId,
                RETAINED_SVG, 800, 600, new double[]{0.0, 0.0, 200.0, 400.0});
        insertFormat(jdbc, changedR1PlanId, "PNG", "ABSENT", "image/png", null,
                null, null, null, null);
        insertFormat(jdbc, changedR1PlanId, "SVG", "PROCESSING", "image/svg+xml", null,
                null, null, null, null);
        insertFormat(jdbc, changedR2PlanId, "PNG", "READY", "image/png", changedR2PngAssetId,
                R2_PNG, 640, 480, new double[]{0.0, 0.0, 120.0, 240.0});
        insertFormat(jdbc, changedR2PlanId, "SVG", "FAILED", "image/svg+xml", null,
                null, null, null, null);

        publish(jdbc, retainedR1PlanId, R1_PUBLISHED_AT);
        publish(jdbc, changedR1PlanId, R1_CHANGED_PUBLISHED_AT);
        publish(jdbc, changedR2PlanId, R2_PUBLISHED_AT);
        setCurrentPlan(jdbc, retainedFloorId, retainedR1PlanId);
        setCurrentPlan(jdbc, changedFloorId, changedR1PlanId);

        return new GraphFixture(r1CatalogId, r2CatalogId, earlyBuildingId, lateBuildingId,
                retainedFloorId, changedFloorId, noPlanFloorId, retainedR1PlanId,
                changedR1PlanId, changedR2PlanId, retainedPngAssetId, retainedSvgAssetId,
                changedR2PngAssetId);
    }

    private NonReadyFixture insertNonReadyGraph(String schema, String state) {
        JdbcTemplate jdbc = jdbc(schema);
        long catalogId = insertCatalog(jdbc, 501L, 3, 7, true);
        long buildingId = insertBuilding(jdbc, "B-NR-" + state, "Non-ready building", 1);
        long floorId = insertFloor(jdbc, buildingId, "F1", "Non-ready floor", 1);
        long planId = insertPlan(jdbc, floorId, 501L, catalogId, "Non-ready plan");
        insertFormat(jdbc, planId, "PNG", state, "image/png", null,
                null, null, null, null);
        insertFormat(jdbc, planId, "SVG", "ABSENT", "image/svg+xml", null,
                null, null, null, null);
        publish(jdbc, planId, R1_PUBLISHED_AT);
        setCurrentPlan(jdbc, floorId, planId);
        return new NonReadyFixture(buildingId, floorId);
    }

    private void switchCurrentCatalogAndFloor(Connection writer, GraphFixture graph) throws SQLException {
        try (PreparedStatement clear = writer.prepareStatement(
                "UPDATE campus_map_catalog_revision SET is_current = FALSE WHERE is_current = TRUE")) {
            clear.executeUpdate();
        }
        try (PreparedStatement select = writer.prepareStatement(
                "UPDATE campus_map_catalog_revision SET is_current = TRUE WHERE id = ?")) {
            select.setLong(1, graph.r2CatalogId());
            select.executeUpdate();
        }
        try (PreparedStatement pointer = writer.prepareStatement(
                "UPDATE campus_map_floor SET current_version_id = ? WHERE id = ?")) {
            pointer.setLong(1, graph.changedR2PlanId());
            pointer.setLong(2, graph.changedFloorId());
            pointer.executeUpdate();
        }
    }

    private static long insertCatalog(JdbcTemplate jdbc,
                                      long revision,
                                      int schemaVersion,
                                      int policyVersion,
                                      boolean current) {
        return jdbc.queryForObject("""
                INSERT INTO campus_map_catalog_revision
                    (revision, schema_version, validation_policy_version, is_current)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, revision, schemaVersion, policyVersion, current);
    }

    private static long insertBuilding(JdbcTemplate jdbc, String code, String label, int displayOrder) {
        return jdbc.queryForObject("""
                INSERT INTO campus_map_building (code, label, display_order)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, code, label, displayOrder);
    }

    private static long insertFloor(JdbcTemplate jdbc,
                                    long buildingId,
                                    String code,
                                    String label,
                                    int displayOrder) {
        return jdbc.queryForObject("""
                INSERT INTO campus_map_floor (building_id, code, label, display_order)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, buildingId, code, label, displayOrder);
    }

    private static long insertPlan(JdbcTemplate jdbc,
                                   long floorId,
                                   long version,
                                   long catalogId,
                                   String label) {
        return jdbc.queryForObject("""
                INSERT INTO campus_map_plan_version
                    (floor_id, version, catalog_revision_id, label)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, floorId, version, catalogId, label);
    }

    private static long insertAsset(JdbcTemplate jdbc,
                                    long planId,
                                    String format,
                                    String contentType,
                                    byte[] content,
                                    int width,
                                    int height) {
        return jdbc.queryForObject("""
                INSERT INTO campus_map_asset
                    (plan_version_id, format, content_type, content, bytes, sha256, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, planId, format, contentType, content, (long) content.length,
                sha256(content), width, height);
    }

    private static void insertFormat(JdbcTemplate jdbc,
                                     long planId,
                                     String format,
                                     String state,
                                     String contentType,
                                     Long assetId,
                                     byte[] content,
                                     Integer width,
                                     Integer height,
                                     double[] viewBox) {
        String viewBoxSql = viewBox == null
                ? "NULL"
                : "ARRAY[?::double precision, ?::double precision, ?::double precision, ?::double precision]";
        String sql = """
                INSERT INTO campus_map_plan_format
                    (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height, view_box)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, %s)
                """.formatted(viewBoxSql);
        if (viewBox == null) {
            jdbc.update(sql, planId, format, state, contentType, assetId,
                    content == null ? 0L : (long) content.length,
                    content == null ? null : sha256(content), width, height);
        } else {
            jdbc.update(sql, planId, format, state, contentType, assetId,
                    (long) content.length, sha256(content), width, height,
                    viewBox[0], viewBox[1], viewBox[2], viewBox[3]);
        }
    }

    private static void publish(JdbcTemplate jdbc, long planId, String publishedAt) {
        jdbc.update("UPDATE campus_map_plan_version SET published_at = ?::timestamptz WHERE id = ?",
                publishedAt, planId);
    }

    private static void setCurrentPlan(JdbcTemplate jdbc, long floorId, long planId) {
        jdbc.update("UPDATE campus_map_floor SET current_version_id = ? WHERE id = ?", planId, floorId);
    }

    private ReadHarness readHarness(String schema, JdbcObservation observation) {
        DataSource observedDataSource = observation.wrap(dataSourceForSchema(schema));
        JdbcTemplate observedJdbc = new JdbcTemplate(observedDataSource);
        CampusMapReadRepository repository = new CampusMapReadRepository(observedJdbc);
        UserRepository users = mock(UserRepository.class);
        when(users.findByIdIncludingArchived(USER_ID)).thenReturn(Optional.of(mock(User.class)));
        CampusMapReadService target = new CampusMapReadService(repository, users);

        TransactionInterceptor transactionAdvice = new TransactionInterceptor();
        transactionAdvice.setTransactionManager(new DataSourceTransactionManager(observedDataSource));
        transactionAdvice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(transactionAdvice);
        CampusMapReadService service = (CampusMapReadService) proxyFactory.getProxy();
        return new ReadHarness(service, repository, observation);
    }

    private String newSchema() {
        String schema = "rct_m3_map_" + SCHEMA_SEQUENCE.incrementAndGet();
        adminJdbc.execute("CREATE SCHEMA " + quote(schema));
        return schema;
    }

    private void migrate(String schema) {
        Flyway.configure()
                .dataSource(dataSourceForSchema(schema))
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .cleanDisabled(true)
                .target("26")
                .load()
                .migrate();
    }

    private JdbcTemplate jdbc(String schema) {
        return new JdbcTemplate(dataSourceForSchema(schema));
    }

    private DriverManagerDataSource dataSourceForSchema(String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        String url = POSTGRES.getJdbcUrl();
        dataSource.setUrl(schema == null
                ? url
                : url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static InternalJwtClaims claims() {
        return new InternalJwtClaims(USER_ID, SESSION_ID, 1L, 1L,
                "STUDENT", "ACTIVE", GROUP_ID, false, false);
    }

    private static void assertMapCode(CampusMapReadException.Code expected, Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CampusMapReadException.class,
                        error -> assertThat(error.code()).isEqualTo(expected));
    }

    private static void assertNoContentQuery(JdbcObservation observation) {
        assertThat(observation.queries()).noneMatch(JdbcObservation::isAssetContentQuery);
        assertThat(observation.contentReads()).isEmpty();
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-256 must be available", error);
        }
    }

    private record GraphFixture(long r1CatalogId,
                                long r2CatalogId,
                                long earlyBuildingId,
                                long lateBuildingId,
                                long retainedFloorId,
                                long changedFloorId,
                                long noPlanFloorId,
                                long retainedR1PlanId,
                                long changedR1PlanId,
                                long changedR2PlanId,
                                long retainedPngAssetId,
                                long retainedSvgAssetId,
                                long changedR2PngAssetId) {
    }

    private record NonReadyFixture(long buildingId, long floorId) {
    }

    private record ReadHarness(CampusMapReadService service,
                               CampusMapReadRepository repository,
                               JdbcObservation observation) {
    }

    private static final class CatalogGate {
        private final CountDownLatch catalogReturned = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private final AtomicBoolean opened = new AtomicBoolean();

        private void afterCatalogQuery() throws SQLException {
            if (!opened.compareAndSet(false, true)) {
                return;
            }
            catalogReturned.countDown();
            try {
                if (!resume.await(15, TimeUnit.SECONDS)) {
                    throw new SQLException("catalog snapshot gate timed out");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new SQLException("catalog snapshot gate interrupted", error);
            }
        }
    }

    private static final class JdbcObservation {
        private final List<String> queries = Collections.synchronizedList(new ArrayList<>());
        private final List<String> events = Collections.synchronizedList(new ArrayList<>());
        private final List<byte[]> contentReads = Collections.synchronizedList(new ArrayList<>());
        private final CatalogGate catalogGate;

        private JdbcObservation() {
            this(null);
        }

        private JdbcObservation(CatalogGate catalogGate) {
            this.catalogGate = catalogGate;
        }

        private DataSource wrap(DataSource delegate) {
            return new ObservedDataSource(delegate, this);
        }

        private void clear() {
            queries.clear();
            events.clear();
            contentReads.clear();
        }

        private void recordQuery(String sql) throws SQLException {
            String normalized = normalize(sql);
            queries.add(normalized);
            events.add("query " + normalized);
            if (catalogGate != null && isCurrentCatalogQuery(normalized)) {
                catalogGate.afterCatalogQuery();
            }
        }

        private void recordRow(String sql) {
            if (sql != null && isAssetMetadataQuery(normalize(sql))) {
                events.add("asset metadata row");
            }
        }

        private void recordContentRead(byte[] content) {
            events.add("content BYTEA read");
            contentReads.add(content == null ? null : content.clone());
        }

        private int indexOf(Predicate<String> predicate) {
            synchronized (queries) {
                for (int index = 0; index < queries.size(); index++) {
                    if (predicate.test(queries.get(index))) {
                        return index;
                    }
                }
            }
            return -1;
        }

        private String queryAt(int index) {
            synchronized (queries) {
                return queries.get(index);
            }
        }

        private int eventIndex(Predicate<String> predicate) {
            synchronized (events) {
                for (int index = 0; index < events.size(); index++) {
                    if (predicate.test(events.get(index))) {
                        return index;
                    }
                }
            }
            return -1;
        }

        private List<String> queries() {
            synchronized (queries) {
                return List.copyOf(queries);
            }
        }

        private List<byte[]> contentReads() {
            synchronized (contentReads) {
                return contentReads.stream()
                        .map(content -> content == null ? null : content.clone())
                        .toList();
            }
        }

        private static boolean isCurrentCatalogQuery(String query) {
            return query.contains("from campus_map_catalog_revision")
                    && query.contains("where is_current = true");
        }

        private static boolean isAssetMetadataQuery(String query) {
            return query.contains("from campus_map_asset")
                    && query.contains("select id, plan_version_id, format, content_type, bytes, sha256");
        }

        private static boolean isAssetContentQuery(String query) {
            return query.contains("select content from campus_map_asset");
        }

        private static boolean isAssetMetadataQueryEvent(String event) {
            return event.startsWith("query ")
                    && isAssetMetadataQuery(event.substring("query ".length()));
        }

        private static boolean isAssetContentQueryEvent(String event) {
            return event.startsWith("query ")
                    && isAssetContentQuery(event.substring("query ".length()));
        }

        private static boolean isAssetMetadataRow(String event) {
            return event.equals("asset metadata row");
        }

        private static boolean isContentByteaRead(String event) {
            return event.equals("content BYTEA read");
        }

        private static String normalize(String sql) {
            return sql.replaceAll("\\s+", " ").trim().toLowerCase();
        }
    }

    private static final class ObservedDataSource implements DataSource {
        private final DataSource delegate;
        private final JdbcObservation observation;

        private ObservedDataSource(DataSource delegate, JdbcObservation observation) {
            this.delegate = delegate;
            this.observation = observation;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return observeConnection(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return observeConnection(delegate.getConnection(username, password));
        }

        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }

        private Connection observeConnection(Connection connection) {
            return proxy(Connection.class, (proxy, method, args) -> {
                Object result = invoke(connection, method, args);
                if (result instanceof PreparedStatement statement
                        && "prepareStatement".equals(method.getName())
                        && args != null
                        && args.length > 0
                        && args[0] instanceof String sql) {
                    return observePreparedStatement(statement, sql);
                }
                if (result instanceof Statement statement && "createStatement".equals(method.getName())) {
                    return observeStatement(statement);
                }
                return result;
            });
        }

        private PreparedStatement observePreparedStatement(PreparedStatement statement, String sql) {
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                if ("executeQuery".equals(method.getName())
                        && (args == null || args.length == 0)) {
                    ResultSet result = (ResultSet) invoke(statement, method, args);
                    observation.recordQuery(sql);
                    return observeResultSet(result, sql);
                }
                Object result = invoke(statement, method, args);
                if (result instanceof ResultSet resultSet && "getResultSet".equals(method.getName())) {
                    return observeResultSet(resultSet);
                }
                return result;
            });
        }

        private Statement observeStatement(Statement statement) {
            return proxy(Statement.class, (proxy, method, args) -> {
                if ("executeQuery".equals(method.getName())
                        && args != null && args.length == 1 && args[0] instanceof String sql) {
                    ResultSet result = (ResultSet) invoke(statement, method, args);
                    observation.recordQuery(sql);
                    return observeResultSet(result, sql);
                }
                Object result = invoke(statement, method, args);
                if (result instanceof ResultSet resultSet && "getResultSet".equals(method.getName())) {
                    return observeResultSet(resultSet);
                }
                return result;
            });
        }

        private ResultSet observeResultSet(ResultSet resultSet) {
            return observeResultSet(resultSet, null);
        }

        private ResultSet observeResultSet(ResultSet resultSet, String sql) {
            return proxy(ResultSet.class, (proxy, method, args) -> {
                Object result = invoke(resultSet, method, args);
                if ("next".equals(method.getName()) && Boolean.TRUE.equals(result)) {
                    observation.recordRow(sql);
                }
                if ("getBytes".equals(method.getName())
                        && args != null
                        && args.length == 1
                        && args[0] instanceof String column
                        && "content".equalsIgnoreCase(column)) {
                    observation.recordContentRead((byte[]) result);
                }
                return result;
            });
        }

        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
            return type.cast(proxy);
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                throw error.getCause();
            }
        }
    }
}
