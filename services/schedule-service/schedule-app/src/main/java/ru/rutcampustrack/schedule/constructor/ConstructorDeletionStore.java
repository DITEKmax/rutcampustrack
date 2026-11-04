package ru.rutcampustrack.schedule.constructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.constructor.grpc.ConstructorDeletionCommand;
import ru.rutcampustrack.constructor.grpc.ConstructorDeletionScope;
import ru.rutcampustrack.constructor.grpc.ConstructorLessonScope;
import ru.rutcampustrack.schedule.contract.dto.item.ConstructorOperationResponse;
import ru.rutcampustrack.schedule.contract.dto.item.ConstructorProtectedReason;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Local snapshots and phase transitions. No RPC is performed while a SQL transaction is held. */
@Service
public class ConstructorDeletionStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcTemplate jdbc;
    private final ScheduleSemesterArchiveWriteFence archive;

    public ConstructorDeletionStore(JdbcTemplate jdbc, ScheduleSemesterArchiveWriteFence archive) {
        this.jdbc = jdbc;
        this.archive = archive;
    }

    public record Snapshot(
            long itemId,
            long groupId,
            long subjectId,
            long semesterId,
            List<Long> familyIds,
            ConstructorDeletionScope scope,
            String manifest,
            Set<Long> protectedOccurrenceIds,
            long protectedCount,
            long removableCount,
            List<ConstructorProtectedReason> reasons) {}

    public record Operation(
            UUID id,
            long actor,
            UUID key,
            long itemId,
            long groupId,
            long subjectId,
            long semesterId,
            List<Long> familyIds,
            byte[] requestHash,
            String revision,
            String manifest,
            ConstructorDeletionCommand attendance,
            ConstructorDeletionCommand academic,
            String state,
            long deletedCount,
            long protectedCount,
            String errorCode) {
        public ConstructorOperationResponse response() {
            return new ConstructorOperationResponse(
                    id, state, deletedCount, protectedCount, errorCode);
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(long itemId) {
        List<Long> family = family(itemId);
        if (family.isEmpty()) throw missing();
        var items =
                jdbc.queryForList(
                        "SELECT * FROM schedule_items WHERE id=ANY(?::bigint[]) ORDER BY id",
                        array(family));
        var first = items.getFirst();
        long group = number(first.get("group_id")),
                subject = number(first.get("subject_id")),
                semester = number(first.get("semester_id"));
        var reasons = new TreeMap<String, Set<Long>>();
        for (var item : items) {
            if (number(item.get("group_id")) != group
                    || number(item.get("subject_id")) != subject
                    || number(item.get("semester_id")) != semester)
                throw ConstructorLifecycleException.conflict("UNKNOWN_HISTORY");
            Boolean created =
                    jdbc.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM schedule_recurring_create_replay WHERE"
                                + " schedule_item_id=?) OR EXISTS(SELECT 1 FROM"
                                + " schedule_assignment_replacement_templates WHERE"
                                + " target_schedule_item_id=?)",
                            Boolean.class,
                            item.get("id"),
                            item.get("id"));
            if (!Boolean.TRUE.equals(created))
                protect(reasons, "UNKNOWN_HISTORY", -number(item.get("id")));
        }
        var rows =
                jdbc.queryForList(
                        "SELECT kind,data::text AS data FROM constructor_deletion_rows(?::bigint[])"
                            + " ORDER BY kind,data::text",
                        array(family));
        var lessons = new ArrayList<JsonNode>();
        var occurrences = new HashMap<Long, JsonNode>();
        var lifecycle = new ArrayList<JsonNode>();
        for (var row : rows) {
            JsonNode value = parse((String) row.get("data"));
            switch ((String) row.get("kind")) {
                case "lessons" -> lessons.add(value);
                case "lesson_occurrences" -> occurrences.put(value.path("id").asLong(), value);
                case "lesson_lifecycle_entries" -> lifecycle.add(value);
                default -> {}
            }
        }
        var occurrenceIds = occurrences.keySet().stream().sorted().toList();
        for (var entry : lifecycle) {
            long occurrence = entry.path("occurrence_id").asLong();
            String action = entry.path("action").asText();
            boolean created =
                    "CREATED".equals(action)
                            && entry.path("revision").asLong() == 1
                            && entry.path("generation").asLong() == 1;
            boolean templateCancelled =
                    "CANCELLED".equals(action)
                            && !entry.path("template_operation_id").isNull()
                            && !entry.path("template_operation_id").isMissingNode();
            if (!created && !templateCancelled)
                protect(
                        reasons,
                        "CANCELLED".equals(action)
                                ? "MANUAL_CANCELLATION"
                                : "TRANSFERRED".equals(action) ? "TRANSFER" : "USER_ACTION",
                        occurrence);
        }
        for (Long occurrence :
                jdbc.query(
                        "SELECT DISTINCT occurrence_id FROM lesson_homework_bindings WHERE"
                            + " occurrence_id=ANY(?::bigint[])",
                        (rs, index) -> rs.getLong(1),
                        array(occurrenceIds))) protect(reasons, "HOMEWORK", occurrence);
        for (Long occurrence :
                jdbc.query(
                        "SELECT DISTINCT occurrence_id FROM lesson_transfer_operations WHERE"
                            + " occurrence_id=ANY(?::bigint[])",
                        (rs, index) -> rs.getLong(1),
                        array(occurrenceIds))) protect(reasons, "TRANSFER", occurrence);
        for (Long occurrence :
                jdbc.query(
                        "SELECT DISTINCT occurrence_id FROM lesson_room_change_ledger WHERE"
                            + " occurrence_id=ANY(?::bigint[])",
                        (rs, index) -> rs.getLong(1),
                        array(occurrenceIds))) protect(reasons, "USER_ACTION", occurrence);
        var scope =
                ConstructorDeletionScope.newBuilder()
                        .setGroupId(group)
                        .setSubjectId(subject)
                        .setSemesterId(semester)
                        .addAllScheduleItemIds(family);
        lessons.sort(java.util.Comparator.comparingLong(value -> value.path("id").asLong()));
        for (var lesson : lessons) {
            long id = lesson.path("id").asLong(),
                    occurrence = lesson.path("occurrence_id").asLong();
            var logical = occurrences.get(occurrence);
            if (logical == null) throw ConstructorLifecycleException.conflict("UNKNOWN_HISTORY");
            var created =
                    lifecycle.stream()
                            .filter(
                                    entry ->
                                            entry.path("occurrence_id").asLong() == occurrence
                                                    && entry.path("action")
                                                            .asText()
                                                            .equals("CREATED")
                                                    && entry.path("revision").asLong() == 1)
                            .findFirst();
            var naturalOrigins =
                    jdbc.queryForList(
                            """
SELECT to_jsonb(origin)::text AS data FROM constructor_generation_origins origin
  JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
 WHERE origin.lesson_id=? AND origin.occurrence_id=? AND operation.state<>'GENERATING'
   AND to_jsonb(operation)->>'source_kind'='SYSTEM_BACKFILL' AND operation.actor_id IS NULL
""",
                            id,
                            occurrence);
            JsonNode systemOrigin =
                    naturalOrigins.size() == 1
                            ? parse((String) naturalOrigins.getFirst().get("data"))
                            : null;
            if (created.isEmpty() && systemOrigin == null)
                protect(reasons, "UNKNOWN_HISTORY", occurrence);
            if (lesson.path("is_geo_blocked").asBoolean()
                    || lesson.path("is_blocked_by_headman").asBoolean())
                protect(reasons, "USER_ACTION", occurrence);
            if ("transferred".equals(lesson.path("status").asText()))
                protect(reasons, "TRANSFER", occurrence);
            long expectedRevision =
                    1
                            + lifecycle.stream()
                                    .filter(
                                            entry ->
                                                    entry.path("lesson_id").asLong() == id
                                                            && "CANCELLED"
                                                                    .equals(
                                                                            entry.path("action")
                                                                                    .asText()))
                                    .count();
            Long bindingRevisions =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM schedule_assignment_binding_ledger WHERE"
                                + " lesson_id=?",
                            Long.class,
                            id);
            Long replacementRevisions =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM schedule_assignment_rebind_ledger WHERE"
                                + " lesson_id=? AND result='MOVED'",
                            Long.class,
                            id);
            expectedRevision += bindingRevisions == null ? 0 : bindingRevisions;
            expectedRevision += replacementRevisions == null ? 0 : replacementRevisions;
            if (lesson.path("revision").asLong() != expectedRevision)
                protect(reasons, "UNKNOWN_HISTORY", occurrence);
            byte[] origin =
                    ConstructorHashes.json(
                            created.orElse(systemOrigin == null ? lesson : systemOrigin));
            scope.addLessons(
                    ConstructorLessonScope.newBuilder()
                            .setLessonId(id)
                            .setOccurrenceId(occurrence)
                            .setScheduleItemId(lesson.path("schedule_item_id").asLong())
                            .setLessonDate(lesson.path("date").asText())
                            .setLessonNumber(lesson.path("lesson_number").asInt())
                            .setLessonType(lesson.path("lesson_type").asText())
                            .setGeneration(lesson.path("generation").asLong())
                            .setLessonRevision(lesson.path("revision").asLong())
                            .setOccurrenceRevision(logical.path("revision").asLong())
                            .setOriginProvenanceHash(ByteString.copyFrom(origin)));
        }
        var allowed = allowedAuditHashes(scope.getLessonsList(), lifecycle);
        allowed.forEach(value -> scope.addAllowedSystemAuditHashes(ByteString.copyFrom(value)));
        String manifest =
                jdbc.queryForObject(
                        "SELECT constructor_deletion_manifest(?::bigint[])::text",
                        String.class,
                        array(family));
        scope.setScheduleManifestDigest(
                ByteString.copyFrom(
                        ConstructorHashes.sha256(manifest.getBytes(StandardCharsets.UTF_8))));
        Set<Long> protectedIds = new HashSet<>();
        reasons.values().forEach(protectedIds::addAll);
        return new Snapshot(
                itemId,
                group,
                subject,
                semester,
                List.copyOf(family),
                scope.build(),
                manifest,
                Set.copyOf(protectedIds),
                protectedIds.size(),
                occurrenceIds.stream().filter(id -> !protectedIds.contains(id)).count(),
                reasons.entrySet().stream()
                        .map(
                                entry ->
                                        new ConstructorProtectedReason(
                                                entry.getKey(), entry.getValue().size()))
                        .toList());
    }

    private List<byte[]> allowedAuditHashes(
            List<ConstructorLessonScope> lessons, List<JsonNode> lifecycle) {
        List<Long> ids = lessons.stream().map(ConstructorLessonScope::getLessonId).toList();
        var hashes = new TreeMap<String, byte[]>();
        for (byte[] hash :
                jdbc.query(
                        "SELECT payload_hash FROM constructor_system_audit_proofs WHERE"
                            + " lesson_id=ANY(?::bigint[])",
                        (rs, index) -> rs.getBytes(1),
                        array(ids))) hashes.put(ConstructorHashes.hex(hash), hash);
        // Legacy constructor effects can be proven from the authoritative local
        // outbox and immutable lifecycle entries, never from a reason string.
        for (String body :
                jdbc.query(
                        "SELECT payload FROM schedule_outbox WHERE"
                            + " event_type='schedule.action-recorded' AND"
                            + " ((payload::jsonb)->'payload'->>'lesson_id')::bigint=ANY(?::bigint[])",
                        (rs, index) -> rs.getString(1),
                        array(ids))) {
            JsonNode envelope = parse(body), payload = envelope.path("payload");
            String action = payload.path("action").asText();
            if (!("LESSON_CREATED".equals(action) || "LESSON_CANCELLED".equals(action))) continue;
            long lesson = payload.path("lesson_id").asLong(),
                    actor = payload.path("actor").path("user_id").asLong();
            if (!"schedule-service".equals(envelope.path("source").asText()) || actor <= 0)
                continue;
            for (JsonNode entry : lifecycle) {
                if (entry.path("lesson_id").asLong() != lesson
                        || entry.path("actor_id").asLong() != actor) continue;
                boolean proven =
                        "LESSON_CREATED".equals(action)
                                        && "CREATED".equals(entry.path("action").asText())
                                        && entry.path("revision").asLong() == 1
                                        && entry.path("generation").asLong() == 1
                                || "LESSON_CANCELLED".equals(action)
                                        && "CANCELLED".equals(entry.path("action").asText())
                                        && !entry.path("template_operation_id").isNull()
                                        && !entry.path("template_operation_id").isMissingNode();
                try {
                    proven &=
                            OffsetDateTime.parse(entry.path("occurred_at").asText())
                                    .toInstant()
                                    .truncatedTo(ChronoUnit.MICROS)
                                    .equals(
                                            OffsetDateTime.parse(
                                                            envelope.path("occurred_at").asText())
                                                    .toInstant()
                                                    .truncatedTo(ChronoUnit.MICROS));
                } catch (RuntimeException unknown) {
                    proven = false;
                }
                if (proven) {
                    byte[] hash = ConstructorHashes.json(payload);
                    hashes.put(ConstructorHashes.hex(hash), hash);
                }
            }
        }
        return List.copyOf(hashes.values());
    }

    @Transactional
    public Operation accept(
            Snapshot observed,
            UUID id,
            long actor,
            UUID key,
            byte[] requestHash,
            String revision,
            ConstructorDeletionCommand attendance,
            ConstructorDeletionCommand academic,
            ScheduleSemesterArchiveWriteFence.PreparedBusinessWrite prepared) {
        archive.lockPreparedBusinessWrite(prepared);
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(1129270869,?)",
                Object.class,
                (actor + ":" + key).hashCode());
        Operation previous = byKey(actor, key);
        if (previous != null) return previous;
        lockFamily(observed.familyIds());
        Snapshot current = snapshot(observed.itemId());
        if (!current.manifest().equals(observed.manifest())
                || !MessageDigest.isEqual(
                        current.scope().toByteArray(), observed.scope().toByteArray()))
            throw ConstructorLifecycleException.conflict("PREVIEW_STALE");
        if (current.protectedCount() > 0)
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_HISTORY_PROTECTED");
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM constructor_deletion_operations WHERE"
                            + " family_ids && ?::bigint[] AND state<>'RELEASED')",
                        Boolean.class,
                        array(current.familyIds()))))
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_OPERATION_PENDING");
        jdbc.update(
                """
INSERT INTO constructor_deletion_operations
    (operation_id,actor_id,request_key,kind,requested_item_id,group_id,subject_id,semester_id,
     family_ids,request_hash,preview_revision,manifest,attendance_command,academic_command,state)
VALUES(?,?,?,'DELETE_UNUSED_SERIES',?,?,?,?,?::bigint[],?,?,?::jsonb,?,?,'PREPARING')
""",
                id,
                actor,
                key,
                observed.itemId(),
                observed.groupId(),
                observed.subjectId(),
                observed.semesterId(),
                array(observed.familyIds()),
                requestHash,
                revision,
                observed.manifest(),
                attendance.toByteArray(),
                academic.toByteArray());
        return byId(id);
    }

    @Transactional
    public boolean state(UUID id, String expected, String next, String error) {
        return jdbc.update(
                        "UPDATE constructor_deletion_operations SET"
                            + " state=?,error_code=?,updated_at=NOW() WHERE operation_id=? AND"
                            + " state=?",
                        next,
                        error,
                        id,
                        expected)
                == 1;
    }

    @Transactional
    public void retry(UUID id) {
        jdbc.update(
                "UPDATE constructor_deletion_operations SET"
                    + " attempts=attempts+1,next_attempt_at=NOW()+make_interval(secs=>LEAST(300,5*power(2,LEAST(attempts,6)))::integer),updated_at=NOW()"
                    + " WHERE operation_id=? AND state NOT IN('COMPLETED','RELEASED')",
                id);
    }

    @Transactional
    public void complete(UUID id) {
        Operation operation = byIdForUpdate(id);
        if (operation == null || "COMPLETED".equals(operation.state())) return;
        if (!"COMMITTING".equals(operation.state()))
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_OPERATION_PENDING");
        ScheduleSemesterArchiveWriteFence.lockSemester(jdbc, operation.semesterId());
        lockFamily(operation.familyIds());
        Boolean unchanged =
                jdbc.queryForObject(
                        "SELECT constructor_deletion_manifest(?::bigint[])=?::jsonb",
                        Boolean.class,
                        array(operation.familyIds()),
                        operation.manifest());
        if (!Boolean.TRUE.equals(unchanged))
            throw ConstructorLifecycleException.conflict("CONSTRUCTOR_LOCAL_WITNESS_CHANGED");
        long deleted =
                jdbc.queryForObject(
                        "SELECT count(*) FROM lesson_occurrences WHERE"
                            + " schedule_item_id=ANY(?::bigint[])",
                        Long.class,
                        array(operation.familyIds()));
        jdbc.queryForObject(
                "SELECT set_config('rutcampustrack.constructor_delete_operation_id',?,TRUE)",
                String.class,
                id.toString());
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
        jdbc.update(
                "DELETE FROM lesson_lifecycle_entries WHERE occurrence_id IN(SELECT id FROM"
                    + " lesson_occurrences WHERE schedule_item_id=ANY(?::bigint[]))",
                array(operation.familyIds()));
        jdbc.update(
                "DELETE FROM lessons WHERE schedule_item_id=ANY(?::bigint[])",
                array(operation.familyIds()));
        jdbc.update(
                "DELETE FROM lesson_occurrences WHERE schedule_item_id=ANY(?::bigint[])",
                array(operation.familyIds()));
        jdbc.update(
                "DELETE FROM schedule_items WHERE id=ANY(?::bigint[])",
                array(operation.familyIds()));
        jdbc.update(
                "UPDATE constructor_deletion_operations SET"
                    + " state='COMPLETED',deleted_count=?,updated_at=NOW() WHERE operation_id=?",
                deleted,
                id);
    }

    public Operation byKey(long actor, UUID key) {
        return operation(
                jdbc.queryForList(
                        "SELECT * FROM constructor_deletion_operations WHERE actor_id=? AND"
                            + " request_key=?",
                        actor,
                        key));
    }

    public void assertNotLegacyLifecycleReplay(long actor, UUID key) {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM schedule_recurring_lifecycle_replay WHERE"
                            + " actor_id=? AND request_key=?)",
                        Boolean.class,
                        actor,
                        key))) {
            throw ConstructorLifecycleException.conflict("LEGACY_CONSTRUCTOR_LIFECYCLE_REPLAY");
        }
    }

    public Operation byId(UUID id) {
        return operation(
                jdbc.queryForList(
                        "SELECT * FROM constructor_deletion_operations WHERE operation_id=?", id));
    }

    private Operation byIdForUpdate(UUID id) {
        return operation(
                jdbc.queryForList(
                        "SELECT * FROM constructor_deletion_operations WHERE operation_id=? FOR"
                            + " UPDATE",
                        id));
    }

    public List<UUID> recoverable() {
        return jdbc.query(
                "SELECT operation_id FROM constructor_deletion_operations WHERE state NOT"
                    + " IN('COMPLETED','RELEASED') AND next_attempt_at<=NOW() ORDER BY"
                    + " next_attempt_at LIMIT 4",
                (rs, index) -> rs.getObject(1, UUID.class));
    }

    public void assertCreateNotDeleted(long actor, UUID key, byte[] payloadHash, long group) {
        var rows =
                jdbc.queryForList(
                        """
SELECT replay.payload_hash,operation.group_id FROM schedule_recurring_create_replay replay
  JOIN constructor_deletion_operations operation ON replay.schedule_item_id=ANY(operation.family_ids)
 WHERE replay.actor_id=? AND replay.request_key=? AND operation.state='COMPLETED'
""",
                        actor,
                        key);
        if (rows.isEmpty()) return;
        if (!MessageDigest.isEqual((byte[]) rows.getFirst().get("payload_hash"), payloadHash))
            throw ConstructorLifecycleException.conflict("IDEMPOTENCY_PAYLOAD_MISMATCH");
        if (number(rows.getFirst().get("group_id")) != group)
            throw ConstructorLifecycleException.conflict("IDEMPOTENCY_PAYLOAD_MISMATCH");
        throw new ConstructorLifecycleException(
                410, "CONSTRUCTOR_SERIES_DELETED", "Этот шаблон окончательно удалён");
    }

    private void lockFamily(List<Long> family) {
        for (Long assignment :
                jdbc.query(
                        "SELECT DISTINCT assignment_id FROM schedule_items WHERE"
                            + " id=ANY(?::bigint[]) ORDER BY assignment_id",
                        (rs, index) -> rs.getLong(1),
                        array(family)))
            jdbc.queryForObject(
                    "SELECT assignment_id FROM schedule_assignment_fences WHERE assignment_id=? FOR"
                        + " UPDATE",
                    Long.class,
                    assignment);
        jdbc.queryForList(
                "SELECT id FROM schedule_items WHERE id=ANY(?::bigint[]) ORDER BY id FOR UPDATE",
                array(family));
        jdbc.queryForList(
                "SELECT id FROM lesson_occurrences WHERE schedule_item_id=ANY(?::bigint[]) ORDER BY"
                    + " id FOR UPDATE",
                array(family));
        jdbc.queryForList(
                "SELECT id FROM lessons WHERE schedule_item_id=ANY(?::bigint[]) ORDER BY"
                    + " occurrence_id,id FOR UPDATE",
                array(family));
        jdbc.queryForList(
                "SELECT binding_id FROM lesson_homework_bindings WHERE occurrence_id IN(SELECT id"
                    + " FROM lesson_occurrences WHERE schedule_item_id=ANY(?::bigint[])) ORDER BY"
                    + " binding_id FOR UPDATE",
                array(family));
    }

    private List<Long> family(long item) {
        return jdbc.query(
                """
WITH RECURSIVE edges(a,b) AS (
    SELECT source_schedule_item_id,target_schedule_item_id FROM schedule_assignment_replacement_templates
    UNION SELECT target_schedule_item_id,source_schedule_item_id FROM schedule_assignment_replacement_templates
), family(id) AS (SELECT id FROM schedule_items WHERE id=?
    UNION SELECT edges.b FROM edges JOIN family ON edges.a=family.id)
SELECT id FROM family ORDER BY id
""",
                (rs, index) -> rs.getLong(1),
                item);
    }

    private static Operation operation(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return null;
        var row = rows.getFirst();
        try {
            Object[] ids = (Object[]) ((java.sql.Array) row.get("family_ids")).getArray();
            return new Operation(
                    (UUID) row.get("operation_id"),
                    number(row.get("actor_id")),
                    (UUID) row.get("request_key"),
                    number(row.get("requested_item_id")),
                    number(row.get("group_id")),
                    number(row.get("subject_id")),
                    number(row.get("semester_id")),
                    java.util.Arrays.stream(ids)
                            .map(value -> ((Number) value).longValue())
                            .toList(),
                    (byte[]) row.get("request_hash"),
                    (String) row.get("preview_revision"),
                    row.get("manifest").toString(),
                    ConstructorDeletionCommand.parseFrom((byte[]) row.get("attendance_command")),
                    ConstructorDeletionCommand.parseFrom((byte[]) row.get("academic_command")),
                    (String) row.get("state"),
                    number(row.get("deleted_count")),
                    number(row.get("protected_count")),
                    (String) row.get("error_code"));
        } catch (Exception invalid) {
            throw new IllegalStateException("Invalid retained constructor operation", invalid);
        }
    }

    private static JsonNode parse(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception invalid) {
            throw ConstructorLifecycleException.conflict("UNKNOWN_HISTORY");
        }
    }

    private static void protect(Map<String, Set<Long>> reasons, String code, long occurrence) {
        reasons.computeIfAbsent(code, ignored -> new HashSet<>()).add(occurrence);
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static String array(List<Long> ids) {
        return "{"
                + ids.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(","))
                + "}";
    }

    private static ConstructorLifecycleException missing() {
        return new ConstructorLifecycleException(
                404, "CONSTRUCTOR_SERIES_NOT_FOUND", "Шаблон не найден");
    }
}
