package ru.rutcampustrack.academic.map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ru.rutcampustrack.academic.map.CampusMapReadModels.Asset;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.AssetMetadata;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Building;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Catalog;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Floor;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.FormatSlot;
import static ru.rutcampustrack.academic.map.CampusMapReadModels.Plan;

/**
 * Read-only JDBC access for the Academic campus-map graph.
 *
 * <p>Every statement is a fixed SQL string and every caller supplied value is
 * bound as a parameter.  In particular, asset metadata and asset bytes have
 * separate queries so a caller can reject an invalid or oversized row before
 * loading the BYTEA.</p>
 */
@Repository
public class CampusMapReadRepository {
    public static final long MAX_ASSET_BYTES = 10L * 1024L * 1024L;

    private static final String CURRENT_CATALOG_SQL = """
            SELECT id, revision, schema_version, validation_policy_version, is_current
            FROM campus_map_catalog_revision
            WHERE is_current = TRUE
            ORDER BY id
            """;

    private static final String CATALOG_BY_ID_SQL = """
            SELECT id, revision, schema_version, validation_policy_version, is_current
            FROM campus_map_catalog_revision
            WHERE id = ?
            """;

    private static final String ACTIVE_BUILDINGS_SQL = """
            SELECT id, label, display_order, is_active
            FROM campus_map_building
            WHERE is_active = TRUE
            ORDER BY display_order, id
            """;

    private static final String ACTIVE_BUILDING_BY_ID_SQL = """
            SELECT id, label, display_order, is_active
            FROM campus_map_building
            WHERE id = ? AND is_active = TRUE
            """;

    private static final String ACTIVE_FLOORS_SQL = """
            SELECT id, building_id, label, display_order, current_version_id, is_active
            FROM campus_map_floor
            WHERE is_active = TRUE
            ORDER BY building_id, display_order, id
            """;

    private static final String ACTIVE_FLOOR_BY_ID_SQL = """
            SELECT id, building_id, label, display_order, current_version_id, is_active
            FROM campus_map_floor
            WHERE id = ? AND is_active = TRUE
            """;

    private static final String CURRENT_PLANS_SQL = """
            SELECT plan.id, plan.floor_id, plan.version, plan.catalog_revision_id,
                   plan.label, plan.published_at
            FROM campus_map_floor AS floor
            JOIN campus_map_plan_version AS plan
              ON plan.id = floor.current_version_id
             AND plan.floor_id = floor.id
            WHERE floor.is_active = TRUE
            ORDER BY floor.id, plan.id
            """;

    private static final String PLAN_BY_FLOOR_AND_VERSION_SQL = """
            SELECT id, floor_id, version, catalog_revision_id, label, published_at
            FROM campus_map_plan_version
            WHERE floor_id = ? AND version = ?
            """;

    private static final String PLAN_BY_ID_AND_FLOOR_SQL = """
            SELECT id, floor_id, version, catalog_revision_id, label, published_at
            FROM campus_map_plan_version
            WHERE id = ? AND floor_id = ?
            """;

    private static final String FORMAT_SLOTS_BY_PLAN_SQL = """
            SELECT id, plan_version_id, format, state, content_type, asset_id,
                   bytes, sha256, width, height, view_box
            FROM campus_map_plan_format
            WHERE plan_version_id = ?
            ORDER BY format, id
            """;

    private static final String ASSET_METADATA_SQL = """
            SELECT id, plan_version_id, format, content_type, bytes, sha256,
                   width, height, octet_length(content) AS content_length
            FROM campus_map_asset
            WHERE id = ? AND plan_version_id = ? AND format = ?
            """;

    private static final String ASSET_CONTENT_SQL = """
            SELECT content
            FROM campus_map_asset
            WHERE id = ? AND plan_version_id = ? AND format = ?
              AND octet_length(content) <= ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public CampusMapReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Catalog> findCurrentCatalogs() {
        return jdbcTemplate.query(CURRENT_CATALOG_SQL, CATALOG_ROW_MAPPER);
    }

    public Optional<Catalog> findCatalogById(long catalogId) {
        return jdbcTemplate.query(CATALOG_BY_ID_SQL, CATALOG_ROW_MAPPER, catalogId)
                .stream()
                .findFirst();
    }

    public List<Building> findActiveBuildings() {
        return jdbcTemplate.query(ACTIVE_BUILDINGS_SQL, BUILDING_ROW_MAPPER);
    }

    public Optional<Building> findActiveBuilding(long buildingId) {
        return jdbcTemplate.query(ACTIVE_BUILDING_BY_ID_SQL, BUILDING_ROW_MAPPER, buildingId)
                .stream()
                .findFirst();
    }

    public List<Floor> findActiveFloors() {
        return jdbcTemplate.query(ACTIVE_FLOORS_SQL, FLOOR_ROW_MAPPER);
    }

    public Optional<Floor> findActiveFloor(long floorId) {
        return jdbcTemplate.query(ACTIVE_FLOOR_BY_ID_SQL, FLOOR_ROW_MAPPER, floorId)
                .stream()
                .findFirst();
    }

    public List<Plan> findCurrentPlans() {
        return jdbcTemplate.query(CURRENT_PLANS_SQL, PLAN_ROW_MAPPER);
    }

    public Optional<Plan> findPlan(long floorId, long version) {
        return jdbcTemplate.query(PLAN_BY_FLOOR_AND_VERSION_SQL, PLAN_ROW_MAPPER, floorId, version)
                .stream()
                .findFirst();
    }

    /** Resolves the floor's current pointer, whose value is the plan row id. */
    public Optional<Plan> findPlanById(long planId, long floorId) {
        return jdbcTemplate.query(PLAN_BY_ID_AND_FLOOR_SQL, PLAN_ROW_MAPPER, planId, floorId)
                .stream()
                .findFirst();
    }

    public List<FormatSlot> findFormatSlots(long planVersionId) {
        return jdbcTemplate.query(FORMAT_SLOTS_BY_PLAN_SQL, FORMAT_SLOT_ROW_MAPPER, planVersionId);
    }

    /**
     * Reads only metadata.  The content column is intentionally absent from
     * this query so validation can reject a row before a BYTEA is materialized.
     */
    public Optional<AssetMetadata> findAssetMetadata(long assetId,
                                                      long planVersionId,
                                                      CampusMapFormat format) {
        return jdbcTemplate.query(
                        ASSET_METADATA_SQL,
                        ASSET_METADATA_ROW_MAPPER,
                        assetId,
                        planVersionId,
                        format.name())
                .stream()
                .findFirst();
    }

    /** Loads a bounded blob only after the caller has validated its metadata. */
    public Optional<Asset> loadAsset(long assetId,
                                     long planVersionId,
                                     CampusMapFormat format) {
        return jdbcTemplate.query(
                        ASSET_CONTENT_SQL,
                        ASSET_ROW_MAPPER,
                        assetId,
                        planVersionId,
                        format.name(),
                        MAX_ASSET_BYTES)
                .stream()
                .findFirst();
    }

    private static final RowMapper<Catalog> CATALOG_ROW_MAPPER = (rs, rowNum) -> new Catalog(
            rs.getLong("id"),
            rs.getLong("revision"),
            rs.getInt("schema_version"),
            rs.getInt("validation_policy_version"),
            rs.getBoolean("is_current"));

    private static final RowMapper<Building> BUILDING_ROW_MAPPER = (rs, rowNum) -> new Building(
            rs.getLong("id"),
            rs.getString("label"),
            rs.getInt("display_order"),
            rs.getBoolean("is_active"));

    private static final RowMapper<Floor> FLOOR_ROW_MAPPER = (rs, rowNum) -> new Floor(
            rs.getLong("id"),
            rs.getLong("building_id"),
            rs.getString("label"),
            rs.getInt("display_order"),
            nullableLong(rs, "current_version_id"),
            rs.getBoolean("is_active"));

    private static final RowMapper<Plan> PLAN_ROW_MAPPER = (rs, rowNum) -> new Plan(
            rs.getLong("id"),
            rs.getLong("floor_id"),
            rs.getLong("version"),
            nullableLong(rs, "catalog_revision_id"),
            rs.getString("label"),
            rs.getObject("published_at", OffsetDateTime.class));

    private static final RowMapper<FormatSlot> FORMAT_SLOT_ROW_MAPPER = (rs, rowNum) -> new FormatSlot(
            rs.getLong("id"),
            rs.getLong("plan_version_id"),
            parseFormat(rs.getString("format")),
            parseState(rs.getString("state")),
            rs.getString("content_type"),
            nullableLong(rs, "asset_id"),
            rs.getLong("bytes"),
            rs.getBytes("sha256"),
            nullableInteger(rs, "width"),
            nullableInteger(rs, "height"),
            readViewBox(rs));

    private static final RowMapper<AssetMetadata> ASSET_METADATA_ROW_MAPPER = (rs, rowNum) -> new AssetMetadata(
            rs.getLong("id"),
            rs.getLong("plan_version_id"),
            parseFormat(rs.getString("format")),
            rs.getString("content_type"),
            rs.getLong("bytes"),
            rs.getBytes("sha256"),
            nullableInteger(rs, "width"),
            nullableInteger(rs, "height"),
            rs.getLong("content_length"));

    private static final RowMapper<Asset> ASSET_ROW_MAPPER = (rs, rowNum) -> new Asset(rs.getBytes("content"));

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static CampusMapFormat parseFormat(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CampusMapFormat.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.DATA_LOSS,
                    "campus map format data is invalid",
                    error);
        }
    }

    private static CampusMapFormatState parseState(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CampusMapFormatState.valueOf(raw);
        } catch (IllegalArgumentException error) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.DATA_LOSS,
                    "campus map format state data is invalid",
                    error);
        }
    }

    private static List<Double> readViewBox(ResultSet rs) throws SQLException {
        Array sqlArray = rs.getArray("view_box");
        if (sqlArray == null) {
            return List.of();
        }
        try {
            Object raw = sqlArray.getArray();
            List<Double> values = new ArrayList<>();
            if (raw instanceof Object[] objects) {
                for (Object value : objects) {
                    if (!(value instanceof Number number)) {
                        throw CampusMapReadException.of(
                                CampusMapReadException.Code.DATA_LOSS,
                                "campus map view box data is invalid");
                    }
                    values.add(number.doubleValue());
                }
            } else if (raw instanceof double[] doubles) {
                for (double value : doubles) {
                    values.add(value);
                }
            } else {
                throw CampusMapReadException.of(
                        CampusMapReadException.Code.DATA_LOSS,
                        "campus map view box data is invalid");
            }
            return List.copyOf(values);
        } finally {
            sqlArray.free();
        }
    }
}
