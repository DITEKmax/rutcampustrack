package ru.rutcampustrack.academic.event;

import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Only the exact Attendance receipt can discharge an admitted enrollment. */
@Component
public class InitialMembershipAttendanceBackfillAcknowledgementConsumer {
    public static final String TYPE = "student.initial-membership.attendance-backfilled";
    private final JdbcTemplate jdbc;

    public InitialMembershipAttendanceBackfillAcknowledgementConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @RabbitListener(queues = RabbitConfig.INITIAL_MEMBERSHIP_BACKFILL_ACK_QUEUE)
    @ru.rutcampustrack.shared.events.EventIdempotent(
            consumer = "academic-initial-membership-backfill-ack")
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        if (envelope == null || !TYPE.equals(envelope.get("event_type"))) return;
        if (!"attendance-service".equals(envelope.get("source"))
                || positive(envelope.get("event_version")) != 1)
            throw new IllegalArgumentException("Untrusted initial membership acknowledgement");
        uuid(envelope.get("event_id"));
        if (!(envelope.get("trace_id") instanceof String trace)
                || trace.isBlank()
                || !(envelope.get("occurred_at") instanceof String occurred))
            throw new IllegalArgumentException(
                    "Missing initial membership acknowledgement correlation");
        java.time.OffsetDateTime.parse(occurred);
        if (!(envelope.get("payload") instanceof Map<?, ?> payload)
                || !payload.keySet()
                        .equals(
                                Set.of(
                                        "source_event_id",
                                        "history_id",
                                        "semester_id",
                                        "payload_hash",
                                        "result"))
                || !"APPLIED".equals(payload.get("result")))
            throw new IllegalArgumentException("Invalid initial membership acknowledgement");
        UUID eventId = uuid(payload.get("source_event_id"));
        long historyId = positive(payload.get("history_id"));
        long semesterId = positive(payload.get("semester_id"));
        if (!(payload.get("payload_hash") instanceof String hash) || !hash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid initial membership acknowledgement hash");
        // Serialize on the same canonical semester fence as PREPARE/SEAL, before the work row.
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(5452097, ?)",
                Object.class,
                (int) (semesterId % Integer.MAX_VALUE));
        var rows =
                jdbc.queryForList(
                        "SELECT history_id, semester_id, payload_hash, state FROM academic_initial_membership_backfills WHERE event_id = ? FOR UPDATE",
                        eventId);
        if (rows.size() != 1)
            throw new IllegalArgumentException("Unknown initial membership acknowledgement");
        var row = rows.getFirst();
        if (((Number) row.get("history_id")).longValue() != historyId
                || ((Number) row.get("semester_id")).longValue() != semesterId
                || !java.security.MessageDigest.isEqual(
                        (byte[]) row.get("payload_hash"), HexFormat.of().parseHex(hash)))
            throw new IllegalArgumentException(
                    "Initial membership acknowledgement identity changed");
        if ("APPLIED".equals(row.get("state"))) return;
        if (!"PENDING".equals(row.get("state")))
            throw new IllegalStateException("Invalid initial membership work state");
        jdbc.update(
                "UPDATE academic_initial_membership_backfills SET state = 'APPLIED', applied_at = NOW() WHERE event_id = ? AND state = 'PENDING'",
                eventId);
    }

    private static long positive(Object raw) {
        if (!(raw instanceof Byte
                        || raw instanceof Short
                        || raw instanceof Integer
                        || raw instanceof Long)
                || ((Number) raw).longValue() <= 0)
            throw new IllegalArgumentException("Expected positive identity integer");
        return ((Number) raw).longValue();
    }

    private static UUID uuid(Object raw) {
        if (!(raw instanceof String text))
            throw new IllegalArgumentException("Expected canonical event UUID");
        UUID parsed = UUID.fromString(text);
        if (!parsed.toString().equals(text))
            throw new IllegalArgumentException("Expected canonical event UUID");
        return parsed;
    }
}
