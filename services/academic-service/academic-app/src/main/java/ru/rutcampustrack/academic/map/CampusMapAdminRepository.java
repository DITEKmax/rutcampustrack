package ru.rutcampustrack.academic.map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Transactional JDBC access for the admin campus-map write path.
 *
 * <p>The existing read repository remains the single reader for the gRPC
 * path.  This repository owns only the additive inventory/version commands
 * and always binds caller values as parameters.</p>
 */
@Repository
public class CampusMapAdminRepository {
    public static final long MAX_ASSET_BYTES = CampusMapReadRepository.MAX_ASSET_BYTES;

    private static final String BUILDINGS_SQL = """
            SELECT id, code, label, display_order, is_active
            FROM campus_map_building
            WHERE is_active = TRUE
            ORDER BY display_order, id
            """;

    private static final String FLOORS_ALL_SQL = """
            SELECT id, building_id, code, label, display_order, current_version_id, is_active
            FROM campus_map_floor
            WHERE is_active = TRUE
            ORDER BY building_id, display_order, id
            """;

    private static final String FLOORS_BY_BUILDING_SQL = """
            SELECT id, building_id, code, label, display_order, current_version_id, is_active
            FROM campus_map_floor
            WHERE is_active = TRUE
              AND building_id = ?
            ORDER BY building_id, display_order, id
            """;

    private static final String FLOOR_BY_ID_FOR_UPDATE_SQL = """
            SELECT id, building_id, code, label, display_order, current_version_id, is_active
            FROM campus_map_floor
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String BUILDING_BY_ID_SQL = """
            SELECT id, code, label, display_order, is_active
            FROM campus_map_building
            WHERE id = ? AND is_active = TRUE
            """;

    private static final String PLAN_BY_FLOOR_AND_VERSION_SQL = """
            SELECT id, floor_id, version, catalog_revision_id, label, published_at
            FROM campus_map_plan_version
            WHERE floor_id = ? AND version = ?
            """;

    private static final String CURRENT_PLAN_SQL = """
            SELECT plan.id, plan.floor_id, plan.version, plan.catalog_revision_id,
                   plan.label, plan.published_at
            FROM campus_map_floor AS floor
            JOIN campus_map_plan_version AS plan
              ON plan.id = floor.current_version_id
             AND plan.floor_id = floor.id
            WHERE floor.id = ?
            """;

    private static final String SLOTS_SQL = """
            SELECT id, plan_version_id, format, state, content_type, asset_id,
                   bytes, sha256, width, height, view_box
            FROM campus_map_plan_format
            WHERE plan_version_id = ?
            ORDER BY format, id
            """;

    private static final String ASSET_SQL = """
            SELECT id, plan_version_id, format, content_type, bytes, sha256,
                   width, height, content
            FROM campus_map_asset
            WHERE id = ? AND plan_version_id = ? AND format = ?
            """;

    private static final String CURRENT_CATALOG_SQL = """
            SELECT id, revision
            FROM campus_map_catalog_revision
            WHERE is_current = TRUE
            FOR UPDATE
            """;

    private final JdbcTemplate jdbcTemplate;

    public CampusMapAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BuildingRow> findActiveBuildings() {
        return jdbcTemplate.query(BUILDINGS_SQL, (rs, rowNum) -> building(rs));
    }

    public List<FloorRow> findActiveFloors(Long buildingId) {
        if (buildingId == null) {
            return jdbcTemplate.query(FLOORS_ALL_SQL, (rs, rowNum) -> floor(rs));
        }
        return jdbcTemplate.query(FLOORS_BY_BUILDING_SQL, (rs, rowNum) -> floor(rs), buildingId);
    }

    public Optional<BuildingRow> findActiveBuilding(long id) {
        return jdbcTemplate.query(BUILDING_BY_ID_SQL, (rs, rowNum) -> building(rs), id)
                .stream().findFirst();
    }

    /** One transaction-scoped lock for the small admin catalog, including its first revision. */
    public void lockCatalogForWrite() {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(1381253965, 1)", rs -> null);
    }

    public void updateBuilding(long id, String code, String label) {
        jdbcTemplate.update("UPDATE campus_map_building SET code = ?, label = ? WHERE id = ?", code, label, id);
    }

    public void updateFloor(long id, String code, String label) {
        jdbcTemplate.update("UPDATE campus_map_floor SET code = ?, label = ? WHERE id = ?", code, label, id);
    }

    public Optional<FloorRow> findFloorForUpdate(long id) {
        return jdbcTemplate.query(FLOOR_BY_ID_FOR_UPDATE_SQL, (rs, rowNum) -> floor(rs), id)
                .stream().findFirst();
    }

    public Optional<PlanRow> findCurrentPlan(long floorId) {
        return jdbcTemplate.query(CURRENT_PLAN_SQL, (rs, rowNum) -> plan(rs), floorId)
                .stream().findFirst();
    }

    public Optional<PlanRow> findPlan(long floorId, long version) {
        return jdbcTemplate.query(PLAN_BY_FLOOR_AND_VERSION_SQL, (rs, rowNum) -> plan(rs), floorId, version)
                .stream().findFirst();
    }

    public List<SlotRow> findSlots(long planId) {
        return jdbcTemplate.query(SLOTS_SQL, (rs, rowNum) -> slot(rs), planId);
    }

    public Optional<AssetRow> findAsset(long assetId, long planId, CampusMapFormat format) {
        return jdbcTemplate.query(ASSET_SQL, (rs, rowNum) -> asset(rs), assetId, planId, format.name())
                .stream().findFirst();
    }

    public boolean existsBuildingCode(String code) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM campus_map_building WHERE code = ?)",
                Boolean.class,
                code);
        return Boolean.TRUE.equals(exists);
    }

    public boolean existsFloorCode(long buildingId, String code) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM campus_map_floor WHERE building_id = ? AND code = ?)",
                Boolean.class,
                buildingId,
                code);
        return Boolean.TRUE.equals(exists);
    }

    public int nextBuildingOrder() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(display_order), -1) + 1 FROM campus_map_building",
                Integer.class);
    }

    public int nextFloorOrder(long buildingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(display_order), -1) + 1 FROM campus_map_floor WHERE building_id = ?",
                Integer.class,
                buildingId);
    }

    public long insertBuilding(String code, String label, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO campus_map_building (code, label, display_order, is_active)
                VALUES (?, ?, ?, TRUE)
                RETURNING id
                """, Long.class, code, label, displayOrder);
    }

    public long insertFloor(long buildingId, String code, String label, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO campus_map_floor (building_id, code, label, display_order, is_active)
                VALUES (?, ?, ?, ?, TRUE)
                RETURNING id
                """, Long.class, buildingId, code, label, displayOrder);
    }

    /** Creates and publishes the next catalog revision, serializing on the current row. */
    public CatalogRevision advanceCatalogRevision() {
        Optional<CatalogRevision> current = jdbcTemplate.query(CURRENT_CATALOG_SQL, (rs, rowNum) ->
                        new CatalogRevision(rs.getLong("id"), rs.getLong("revision")))
                .stream().findFirst();
        current.ifPresent(row -> jdbcTemplate.update(
                "UPDATE campus_map_catalog_revision SET is_current = FALSE WHERE id = ?",
                row.id()));
        Long nextRevision = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(revision), 0) + 1 FROM campus_map_catalog_revision",
                Long.class);
        if (nextRevision == null || nextRevision <= 0) {
            throw new IllegalStateException("campus map catalog revision cannot advance");
        }
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO campus_map_catalog_revision
                    (revision, schema_version, validation_policy_version, is_current)
                VALUES (?, 1, 1, TRUE)
                RETURNING id
                """, Long.class, nextRevision);
        if (id == null) {
            throw new IllegalStateException("campus map catalog revision insert returned no id");
        }
        return new CatalogRevision(id, nextRevision);
    }

    public Long catalogRevisionValue(long catalogId) {
        return jdbcTemplate.query(
                        "SELECT revision FROM campus_map_catalog_revision WHERE id = ?",
                        (rs, rowNum) -> rs.getLong("revision"),
                        catalogId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public long insertPlan(long floorId, long version, long catalogRevisionId, String label) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO campus_map_plan_version
                    (floor_id, version, catalog_revision_id, label, published_at)
                VALUES (?, ?, ?, ?, NULL)
                RETURNING id
                """, Long.class, floorId, version, catalogRevisionId, label);
    }

    public long insertAsset(long planId,
                            CampusMapFormat format,
                            String contentType,
                            byte[] content,
                            long bytes,
                            byte[] sha256,
                            Integer width,
                            Integer height) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO campus_map_asset
                    (plan_version_id, format, content_type, content, bytes, sha256, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, planId, format.name(), contentType, content, bytes, sha256, width, height);
    }

    public void insertSlot(long planId,
                           CampusMapFormat format,
                           CampusMapFormatState state,
                           String contentType,
                           Long assetId,
                           long bytes,
                           byte[] sha256,
                           Integer width,
                           Integer height) {
        jdbcTemplate.update("""
                INSERT INTO campus_map_plan_format
                    (plan_version_id, format, state, content_type, asset_id, bytes, sha256, width, height)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, planId, format.name(), state.name(), contentType, assetId, bytes, sha256, width, height);
    }

    public void publishPlan(long planId) {
        jdbcTemplate.update("UPDATE campus_map_plan_version SET published_at = NOW() WHERE id = ?", planId);
    }

    public void pointFloorAt(long floorId, long planId) {
        jdbcTemplate.update("UPDATE campus_map_floor SET current_version_id = ? WHERE id = ?", planId, floorId);
    }

    private static BuildingRow building(ResultSet rs) throws SQLException {
        return new BuildingRow(rs.getLong("id"), rs.getString("code"), rs.getString("label"),
                rs.getInt("display_order"), rs.getBoolean("is_active"));
    }

    private static FloorRow floor(ResultSet rs) throws SQLException {
        return new FloorRow(rs.getLong("id"), rs.getLong("building_id"), rs.getString("code"),
                rs.getString("label"), rs.getInt("display_order"), nullableLong(rs, "current_version_id"),
                rs.getBoolean("is_active"));
    }

    private static PlanRow plan(ResultSet rs) throws SQLException {
        return new PlanRow(rs.getLong("id"), rs.getLong("floor_id"), rs.getLong("version"),
                nullableLong(rs, "catalog_revision_id"), rs.getString("label"),
                rs.getObject("published_at", OffsetDateTime.class));
    }

    private static SlotRow slot(ResultSet rs) throws SQLException {
        return new SlotRow(rs.getLong("id"), rs.getLong("plan_version_id"),
                CampusMapFormat.valueOf(rs.getString("format")),
                CampusMapFormatState.valueOf(rs.getString("state")), rs.getString("content_type"),
                nullableLong(rs, "asset_id"), rs.getLong("bytes"), rs.getBytes("sha256"),
                nullableInt(rs, "width"), nullableInt(rs, "height"));
    }

    private static AssetRow asset(ResultSet rs) throws SQLException {
        return new AssetRow(rs.getLong("id"), rs.getLong("plan_version_id"),
                CampusMapFormat.valueOf(rs.getString("format")), rs.getString("content_type"),
                rs.getLong("bytes"), rs.getBytes("sha256"), nullableInt(rs, "width"),
                nullableInt(rs, "height"), rs.getBytes("content"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record BuildingRow(long id, String code, String label, int displayOrder, boolean active) {
    }

    public record FloorRow(long id, long buildingId, String code, String label, int displayOrder,
                           Long currentVersionId, boolean active) {
    }

    public record PlanRow(long id, long floorId, long version, Long catalogRevisionId, String label,
                          OffsetDateTime publishedAt) {
    }

    public record SlotRow(long id, long planVersionId, CampusMapFormat format, CampusMapFormatState state,
                          String contentType, Long assetId, long bytes, byte[] sha256, Integer width,
                          Integer height) {
    }

    public record AssetRow(long id, long planVersionId, CampusMapFormat format, String contentType,
                           long bytes, byte[] sha256, Integer width, Integer height, byte[] content) {
    }

    public record CatalogRevision(long id, long revision) {
    }
}
