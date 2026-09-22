package ru.rutcampustrack.academic.map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** JDBC boundary for map-open idempotency and the shared daily aggregate. */
@Repository
public class CampusMapUsageRepository {
    private static final String INSERT_INTENT_SQL = """
            INSERT INTO campus_map_open_intent
                (owner_hmac, intent_id, building_id, floor_id, payload_hash,
                 accepted_utc_day, accepted_at, expires_at)
            VALUES (?, ?, ?, ?, ?, (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '48 hours')
            ON CONFLICT (owner_hmac, intent_id) DO NOTHING
            """;

    private static final String LOCK_INTENT_SQL = """
            SELECT owner_hmac, intent_id, building_id, floor_id, payload_hash,
                   accepted_utc_day, accepted_at, expires_at
            FROM campus_map_open_intent
            WHERE owner_hmac = ? AND intent_id = ?
            FOR UPDATE
            """;

    private static final String INSERT_DEDUPE_SQL = """
            INSERT INTO campus_map_floor_demand_dedupe
                (owner_hmac, floor_id, utc_day, intent_id, accepted_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (owner_hmac, intent_id) DO NOTHING
            """;

    private static final String FLOOR_COUNTS_SQL = """
            SELECT floor_id, COALESCE(SUM(open_count), 0) AS open_count
            FROM campus_map_floor_daily_demand
            GROUP BY floor_id
            ORDER BY floor_id
            """;

    private static final String TOTAL_COUNT_SQL = """
            SELECT COALESCE(SUM(open_count), 0)
            FROM campus_map_floor_daily_demand
            """;

    private static final String DELETE_EXPIRED_DEDUPE_SQL = """
            DELETE FROM campus_map_floor_demand_dedupe
            WHERE (utc_day::timestamp + INTERVAL '3 days')
                    <= (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
            """;

    private static final String DELETE_EXPIRED_INTENT_SQL = """
            DELETE FROM campus_map_open_intent
            WHERE expires_at <= CURRENT_TIMESTAMP
            """;

    private final JdbcTemplate jdbcTemplate;

    public CampusMapUsageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertOpenIntent(byte[] ownerHmac,
                                UUID intentId,
                                long buildingId,
                                long floorId,
                                byte[] payloadHash) {
        return jdbcTemplate.update(INSERT_INTENT_SQL,
                ownerHmac, intentId, buildingId, floorId, payloadHash);
    }

    public Optional<OpenIntentRow> findOpenIntentForUpdate(byte[] ownerHmac, UUID intentId) {
        return jdbcTemplate.query(LOCK_INTENT_SQL, (rs, rowNum) -> openIntent(rs),
                        ownerHmac, intentId)
                .stream()
                .findFirst();
    }

    public int insertDemandDedupe(byte[] ownerHmac,
                                  long floorId,
                                  LocalDate utcDay,
                                  UUID intentId,
                                  OffsetDateTime acceptedAt) {
        return jdbcTemplate.update(INSERT_DEDUPE_SQL,
                ownerHmac, floorId, utcDay, intentId, acceptedAt);
    }

    public Map<Long, Long> findAllTimeFloorCounts() {
        List<FloorCount> rows = jdbcTemplate.query(FLOOR_COUNTS_SQL,
                (rs, rowNum) -> new FloorCount(rs.getLong("floor_id"), rs.getLong("open_count")));
        Map<Long, Long> result = new LinkedHashMap<>();
        for (FloorCount row : rows) {
            result.put(row.floorId(), row.openCount());
        }
        return result;
    }

    public long findAllTimeTotal() {
        Long value = jdbcTemplate.queryForObject(TOTAL_COUNT_SQL, Long.class);
        return value == null ? 0L : value;
    }

    /**
     * Deletes retained child rows before their immutable parent rows.  The
     * database guards enforce the same UTC-plus-three-days and accepted-plus-
     * forty-eight-hours boundaries, so a scheduler retry cannot shorten them.
     */
    public int deleteExpiredDemandDedupe() {
        return jdbcTemplate.update(DELETE_EXPIRED_DEDUPE_SQL);
    }

    public int deleteExpiredOpenIntents() {
        return jdbcTemplate.update(DELETE_EXPIRED_INTENT_SQL);
    }

    private static OpenIntentRow openIntent(ResultSet rs) throws SQLException {
        return new OpenIntentRow(
                rs.getBytes("owner_hmac"),
                rs.getObject("intent_id", UUID.class),
                rs.getLong("building_id"),
                rs.getLong("floor_id"),
                rs.getBytes("payload_hash"),
                rs.getObject("accepted_utc_day", LocalDate.class),
                rs.getObject("accepted_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class));
    }

    public record OpenIntentRow(byte[] ownerHmac,
                                UUID intentId,
                                long buildingId,
                                long floorId,
                                byte[] payloadHash,
                                LocalDate acceptedUtcDay,
                                OffsetDateTime acceptedAt,
                                OffsetDateTime expiresAt) {
        public OpenIntentRow {
            ownerHmac = ownerHmac == null ? null : ownerHmac.clone();
            payloadHash = payloadHash == null ? null : payloadHash.clone();
        }

        @Override
        public byte[] ownerHmac() {
            return ownerHmac == null ? null : ownerHmac.clone();
        }

        @Override
        public byte[] payloadHash() {
            return payloadHash == null ? null : payloadHash.clone();
        }
    }

    public record FloorCount(long floorId, long openCount) {
    }
}
