package ru.rutcampustrack.schedule.constructor;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Exact trusted constructor effects survive outbox retention as opaque payload hashes. */
public final class ConstructorSystemAuditProofs {
    private ConstructorSystemAuditProofs() {}

    public static void remember(
            JdbcTemplate jdbc,
            String action,
            long lesson,
            Long actor,
            Map<String, Object> payload,
            OffsetDateTime occurredAt) {
        if (!("LESSON_CREATED".equals(action) || "LESSON_CANCELLED".equals(action))) return;
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT to_regclass('constructor_system_audit_proofs') IS NOT NULL",
                        Boolean.class))) return;
        if (actor == null) {
            if (!"LESSON_CREATED".equals(action)) return;
            var origins =
                    jdbc.queryForList(
                            """
SELECT origin.occurrence_id,origin.operation_id,origin.created_at
  FROM constructor_generation_origins origin
  JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
 WHERE origin.lesson_id=? AND to_jsonb(operation)->>'source_kind'='SYSTEM_BACKFILL'
   AND operation.actor_id IS NULL
   AND (operation.state<>'GENERATING' OR operation.operation_id=
       NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id',TRUE),'')::uuid)
""",
                            lesson);
            for (var origin : origins) {
                var stored = instant(origin.get("created_at"));
                if (!stored.truncatedTo(ChronoUnit.MICROS)
                        .equals(occurredAt.toInstant().truncatedTo(ChronoUnit.MICROS))) continue;
                jdbc.update(
                        "INSERT INTO"
                            + " constructor_system_audit_proofs(payload_hash,lesson_id,occurrence_id,origin_operation_id,source_kind,action)"
                            + " VALUES(?,?,?,?,'SYSTEM_BACKFILL',?) ON CONFLICT(payload_hash) DO"
                            + " NOTHING",
                        ConstructorHashes.json(payload),
                        lesson,
                        origin.get("occurrence_id"),
                        origin.get("operation_id"),
                        action);
            }
            return;
        }
        var entries =
                jdbc.queryForList(
                        """
                        SELECT id,occurrence_id,occurred_at FROM lesson_lifecycle_entries
                         WHERE lesson_id=? AND actor_id=? AND action=?
                           AND ((?='LESSON_CREATED' AND revision=1 AND generation=1)
                               OR (?='LESSON_CANCELLED' AND template_operation_id IS NOT NULL))
                         ORDER BY revision
                        """,
                        lesson,
                        actor,
                        "LESSON_CREATED".equals(action) ? "CREATED" : "CANCELLED",
                        action,
                        action);
        for (var entry : entries) {
            var stored = instant(entry.get("occurred_at"));
            if (!stored.truncatedTo(ChronoUnit.MICROS)
                    .equals(occurredAt.toInstant().truncatedTo(ChronoUnit.MICROS))) continue;
            jdbc.update(
                    "INSERT INTO"
                        + " constructor_system_audit_proofs(payload_hash,lesson_id,occurrence_id,lifecycle_entry_id,action)"
                        + " VALUES(?,?,?,?,?) ON CONFLICT(payload_hash) DO NOTHING",
                    ConstructorHashes.json(payload),
                    lesson,
                    entry.get("occurrence_id"),
                    entry.get("id"),
                    action);
        }
    }

    private static java.time.Instant instant(Object value) {
        if (value instanceof OffsetDateTime timestamp) return timestamp.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.Instant timestamp) return timestamp;
        return OffsetDateTime.parse(value.toString()).toInstant();
    }
}
