package ru.rutcampustrack.academic.map;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.academic.contract.dto.map.CampusMapAdminModels.DeletionTarget;

/** Final deletion stays in the same PostgreSQL transaction as its durable replay receipt. */
@Repository
public class CampusMapDeletionRepository {
    private final JdbcTemplate jdbc;

    public CampusMapDeletionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Counts counts(long floorId, long buildingId) {
        return jdbc.queryForObject(
                """
                SELECT (SELECT COUNT(*) FROM campus_map_plan_version WHERE floor_id = ?) AS versions,
                       (SELECT COUNT(*) FROM campus_map_asset a JOIN campus_map_plan_version p
                           ON p.id = a.plan_version_id WHERE p.floor_id = ?) AS assets,
                       (SELECT COALESCE(SUM(a.bytes), 0) FROM campus_map_asset a JOIN campus_map_plan_version p
                           ON p.id = a.plan_version_id WHERE p.floor_id = ?) AS bytes,
                       (SELECT COALESCE(SUM(open_count), 0) FROM campus_map_floor_daily_demand
                           WHERE floor_id = ?) AS opens,
                       (SELECT COUNT(*) FROM campus_map_floor WHERE building_id = ? AND id <> ?) AS remaining,
                       (SELECT COALESCE(MAX(revision), 0) FROM campus_map_catalog_revision) AS revision
                """,
                (rs, row) ->
                        new Counts(
                                rs.getLong("versions"),
                                rs.getLong("assets"),
                                rs.getLong("bytes"),
                                rs.getLong("opens"),
                                rs.getLong("remaining"),
                                rs.getLong("revision")),
                floorId,
                floorId,
                floorId,
                floorId,
                buildingId,
                floorId);
    }

    public Optional<Receipt> receipt(UUID operationId) {
        return jdbc
                .query(
                        """
                SELECT owner_id, target_type, target_id, preview_digest, action_kind, building_code, floor_code, format
                FROM campus_map_deletion_receipt WHERE operation_id = ?
                """,
                        (rs, row) ->
                                new Receipt(
                                        rs.getLong("owner_id"),
                                        DeletionTarget.valueOf(rs.getString("target_type")),
                                        rs.getLong("target_id"),
                                        rs.getString("preview_digest"),
                                        rs.getString("action_kind"),
                                        rs.getString("building_code"),
                                        rs.getString("floor_code"),
                                        rs.getString("format")),
                        operationId)
                .stream()
                .findFirst();
    }

    public void insertPlanReceipt(
            UUID operationId,
            long ownerId,
            long floorId,
            String digest,
            String buildingCode,
            String floorCode,
            String format) {
        jdbc.update(
                """
                INSERT INTO campus_map_deletion_receipt
                    (operation_id, owner_id, target_type, target_id, preview_digest, action_kind, building_code, floor_code, format)
                VALUES (?, ?, 'FLOOR', ?, ?, 'PLAN_CLEAR', ?, ?, ?)
                """,
                operationId,
                ownerId,
                floorId,
                digest,
                buildingCode,
                floorCode,
                format);
    }

    public void insertReceipt(
            UUID operationId, long ownerId, DeletionTarget type, long targetId, String digest) {
        jdbc.update(
                """
                INSERT INTO campus_map_deletion_receipt
                    (operation_id, owner_id, target_type, target_id, preview_digest)
                VALUES (?, ?, ?, ?, ?)
                """,
                operationId,
                ownerId,
                type.name(),
                targetId,
                digest);
    }

    public void deleteFloor(long floorId) {
        // The local scope expires on commit/rollback. Database guards still protect every other
        // floor.
        jdbc.query(
                "SELECT set_config('rutcampustrack.map_delete_floor_id', ?, true)",
                rs -> null,
                Long.toString(floorId));
        jdbc.update("UPDATE campus_map_floor SET current_version_id = NULL WHERE id = ?", floorId);
        jdbc.update(
                "DELETE FROM campus_map_plan_format WHERE plan_version_id IN "
                        + "(SELECT id FROM campus_map_plan_version WHERE floor_id = ?)",
                floorId);
        jdbc.update(
                "DELETE FROM campus_map_asset WHERE plan_version_id IN "
                        + "(SELECT id FROM campus_map_plan_version WHERE floor_id = ?)",
                floorId);
        jdbc.update("DELETE FROM campus_map_plan_version WHERE floor_id = ?", floorId);
        jdbc.update("DELETE FROM campus_map_floor_demand_dedupe WHERE floor_id = ?", floorId);
        jdbc.update("DELETE FROM campus_map_open_intent WHERE floor_id = ?", floorId);
        jdbc.update("DELETE FROM campus_map_floor_daily_demand WHERE floor_id = ?", floorId);
        jdbc.update("DELETE FROM campus_map_floor WHERE id = ?", floorId);
    }

    public void deleteEmptyBuilding(long buildingId) {
        jdbc.update("DELETE FROM campus_map_building WHERE id = ?", buildingId);
    }

    public record Counts(
            long versions,
            long assets,
            long bytes,
            long opens,
            long remainingFloors,
            long revision) {}

    public record Receipt(
            long ownerId,
            DeletionTarget type,
            long targetId,
            String digest,
            String actionKind,
            String buildingCode,
            String floorCode,
            String format) {
        public Receipt(long ownerId, DeletionTarget type, long targetId, String digest) {
            this(ownerId, type, targetId, digest, "INVENTORY", null, null, null);
        }
    }
}
