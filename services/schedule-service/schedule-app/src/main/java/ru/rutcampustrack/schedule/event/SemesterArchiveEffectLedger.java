package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Durable proof ledger for the few Schedule events whose effects cross services. */
@Service
public class SemesterArchiveEffectLedger {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SemesterArchiveEffectLedger(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(DomainEvent event, String serializedEnvelope) {
        String eventType = event.getEventType();
        String target = targetFor(eventType);
        if (target == null) return;

        JsonNode envelope = readEnvelope(serializedEnvelope);
        JsonNode payload = envelope.path("payload");
        Long semesterId = semesterId(payload);
        long scopeId = semesterId == null ? 0L : semesterId;
        byte[] payloadHash = sha256(canonicalJson(payload));
        String blockingReason = semesterId == null
                ? "Событие " + eventType + " не содержит подтверждённую semester scope"
                : null;
        int inserted = jdbcTemplate.update("""
                INSERT INTO schedule_semester_archive_effect_ledger
                    (event_id, target, event_type, semester_id, scope_id, payload_hash, event_payload, blocking_reason)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                ON CONFLICT (event_id, target, event_type, scope_id) DO NOTHING
                """, event.getEventId(), target, eventType, semesterId, scopeId,
                payloadHash, serializedEnvelope, blockingReason);
        if (inserted == 0) {
            byte[] existingHash = jdbcTemplate.queryForObject("""
                    SELECT payload_hash FROM schedule_semester_archive_effect_ledger
                     WHERE event_id = ? AND target = ? AND event_type = ? AND scope_id = ?
                    """, byte[].class, event.getEventId(), target, eventType, scopeId);
            if (existingHash == null || !Arrays.equals(existingHash, payloadHash)) {
                throw new IllegalStateException("Schedule effect event identity was reused with different payload");
            }
        }
    }

    @Transactional
    public void acknowledge(UUID sourceEventId,
                            String target,
                            String eventType,
                            long semesterId,
                            String payloadHashHex,
                            String result,
                            String blockingReason,
                            UUID acknowledgementEventId) {
        if (sourceEventId == null || acknowledgementEventId == null || semesterId <= 0
                || (!"ATTENDANCE".equals(target) && !"ACADEMIC".equals(target))
                || (!"APPLIED".equals(result) && !"ERROR".equals(result))) {
            throw new IllegalArgumentException("Archive effect acknowledgement identity is invalid");
        }
        byte[] payloadHash;
        if (payloadHashHex == null || !payloadHashHex.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Archive effect acknowledgement hash must be lowercase SHA-256");
        }
        try {
            payloadHash = HexFormat.of().parseHex(payloadHashHex);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Archive effect acknowledgement hash is invalid", malformed);
        }
        if (payloadHash.length != 32) {
            throw new IllegalArgumentException("Archive effect acknowledgement hash must be SHA-256");
        }

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT state, payload_hash, blocking_reason, receipt_event_id
                  FROM schedule_semester_archive_effect_ledger
                 WHERE event_id = ? AND target = ? AND event_type = ? AND scope_id = ?
                 FOR UPDATE
                """, sourceEventId, target, eventType, semesterId);
        if (!Arrays.equals((byte[]) row.get("payload_hash"), payloadHash)) {
            throw new IllegalArgumentException("Archive effect acknowledgement payload identity does not match");
        }
        String state = String.valueOf(row.get("state"));
        if ("APPLIED".equals(state)) return;

        String nextState = "APPLIED".equals(result) ? "APPLIED" : "ERROR";
        jdbcTemplate.update("""
                UPDATE schedule_semester_archive_effect_ledger
                   SET state = ?, receipt_event_id = ?, blocking_reason = ?,
                       applied_at = CASE WHEN ? = 'APPLIED' THEN now() ELSE NULL END
                 WHERE event_id = ? AND target = ? AND event_type = ? AND scope_id = ?
                """, nextState, "APPLIED".equals(result) ? acknowledgementEventId : null,
                "APPLIED".equals(result) ? null : normalizeReason(blockingReason), nextState,
                sourceEventId, target, eventType, semesterId);
    }

    @Transactional(readOnly = true)
    public String firstUnprovenEffectReason(long semesterId) {
        return jdbcTemplate.query("""
                SELECT reason FROM (
                    SELECT COALESCE(blocking_reason,
                            'Ожидается точное подтверждение эффекта ' || event_type) AS reason,
                           created_at AS ordered_at
                      FROM schedule_semester_archive_effect_ledger
                     WHERE (semester_id = ? OR semester_id IS NULL) AND state <> 'APPLIED'
                    UNION ALL
                    SELECT 'В outbox сохранено старое событие ' || outbox.event_type
                               || ' без точного effect receipt' AS reason,
                           outbox.created_at AS ordered_at
                      FROM schedule_outbox outbox
                     WHERE outbox.event_type IN ('lesson.closed', 'lesson.cancelled', 'lesson.deleted',
                             'lesson.one_off.cancelled', 'homework.binding.archived')
                       AND (NULLIF(outbox.payload #>> '{payload,semester_id}', '') IS NULL
                            OR outbox.payload #>> '{payload,semester_id}' !~ '^[1-9][0-9]*$'
                            OR outbox.payload #>> '{payload,semester_id}' = ?)
                       AND NOT EXISTS (
                            SELECT 1 FROM schedule_semester_archive_effect_ledger receipt
                             WHERE receipt.event_id::TEXT = outbox.payload ->> 'event_id'
                               AND receipt.event_type = outbox.event_type)
                ) unproven
                 ORDER BY ordered_at
                 LIMIT 1
                """, resultSet -> resultSet.next() ? resultSet.getString("reason") : null,
                semesterId, Long.toString(semesterId));
    }

    private JsonNode readEnvelope(String serializedEnvelope) {
        try {
            return objectMapper.readTree(serializedEnvelope);
        } catch (JsonProcessingException invalid) {
            throw new IllegalStateException("Serialized Schedule event envelope is invalid JSON", invalid);
        }
    }

    private Long semesterId(JsonNode payload) {
        JsonNode raw = payload.path("semester_id");
        if (!raw.isIntegralNumber() || !raw.canConvertToLong()) return null;
        long id = raw.longValue();
        return id > 0 ? id : null;
    }

    private static String targetFor(String eventType) {
        return switch (eventType) {
            case "lesson.closed", "lesson.cancelled", "lesson.deleted", "lesson.one_off.cancelled" -> "ATTENDANCE";
            case "homework.binding.archived" -> "ACADEMIC";
            default -> null;
        };
    }

    byte[] canonicalJson(JsonNode value) {
        try {
            return objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(sortObjectKeys(value));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Could not encode canonical event payload", impossible);
        }
    }

    private static JsonNode sortObjectKeys(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            java.util.List<String> keys = new java.util.ArrayList<>();
            Iterator<String> fields = value.fieldNames();
            while (fields.hasNext()) keys.add(fields.next());
            keys.sort(String::compareTo);
            for (String key : keys) sorted.set(key, sortObjectKeys(value.get(key)));
            return sorted;
        }
        if (value.isArray()) {
            com.fasterxml.jackson.databind.node.ArrayNode sorted =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (JsonNode item : value) sorted.add(sortObjectKeys(item));
            return sorted;
        }
        return value.deepCopy();
    }

    static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return "Участник не подтвердил применение события";
        return reason.length() <= 1000 ? reason : reason.substring(0, 1000);
    }
}
