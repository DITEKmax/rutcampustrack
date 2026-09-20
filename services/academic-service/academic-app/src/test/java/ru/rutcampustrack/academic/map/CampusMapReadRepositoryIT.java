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
