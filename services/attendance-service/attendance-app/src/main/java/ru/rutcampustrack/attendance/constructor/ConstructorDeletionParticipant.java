package ru.rutcampustrack.attendance.constructor;

import com.google.protobuf.ByteString;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.constructor.grpc.*;

/** Exact physical-lesson participant. Control receipts/fences survive business deletion. */
@Service
public class ConstructorDeletionParticipant {
    public static final String OPERATIONS = "constructor_deletion_participants";
    private static final String PAIRS = "student_checkin_pairs";
    private final MongoTemplate mongo;
    private final PairWriteCoordinator pairs;
    private final Clock clock;
    private final ru.rutcampustrack.shared.outbox.OutboxStorage outbox;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    public ConstructorDeletionParticipant(MongoTemplate mongo, PairWriteCoordinator pairs, Clock clock,
            ru.rutcampustrack.shared.outbox.OutboxStorage outbox, com.fasterxml.jackson.databind.ObjectMapper mapper) {
        this.mongo = mongo; this.pairs = pairs; this.clock = clock; this.outbox = outbox; this.mapper = mapper;
    }

    /** Exact persisted admission, also observable by direct recovery/rollback checks. */
    public boolean isAdmitted(ConstructorDeletionCommand command) {
        validate(command);
        Document epoch = mongo.findById(command.getOperationId(), Document.class, OPERATIONS);
        return epoch != null && identity(command).equals(epoch.getString("identity"));
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public ConstructorParticipantReceipt apply(ConstructorDeletionCommand c) {
        Map<Long, ConstructorLessonScope> lessons = validate(c);
        var action = c.getAction();
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW) return snapshot(c, lessons).receipt(c, "PREVIEW");
        Document epoch = mongo.findById(c.getOperationId(), Document.class, OPERATIONS);
        if (epoch != null) {
            if (!identity(c).equals(epoch.getString("identity"))) throw conflict();
            String state = epoch.getString("state");
            if ("DELETED".equals(state)) {
                if (action != ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT) throw conflict();
                return receipt(c, epoch);
            }
            if ("RELEASED".equals(state)) {
                if (action != ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE) throw conflict();
                return receipt(c, epoch);
            }
            if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE) return receipt(c, epoch);
        }
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_RELEASE) {
            if (epoch == null) {
                // Same unique operation document as PREPARE: late admission cannot resurrect it.
                epoch = epoch(c, "RELEASED");
                mongo.insert(epoch, OPERATIONS);
            } else {
                touchOwnedFences(c, lessons.keySet(), false);
                mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getOperationId())), new Update().set("state", "RELEASED"), OPERATIONS);
                epoch.put("state", "RELEASED");
            }
            return receipt(c, epoch);
        }
        if (epoch == null && action != ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE) throw conflict();
        if (epoch == null) pairs.lockLessons(c.getScope().getSemesterId(), lessons.keySet(), c.getScope().getGroupId(), clock.instant());
        else touchOwnedFences(c, lessons.keySet(), true);
        Snapshot current = snapshot(c, lessons);
        if (!current.digest.equals(c.getParticipantDigest()) || !current.protectedLessons.isEmpty()) throw conflict();
        if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE) {
            epoch = epoch(c, "PREPARED");
            mongo.insert(epoch, OPERATIONS);
            for (long id : lessons.keySet()) mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(PairWriteCoordinator.lessonFenceId(id))),
                    new Update().set("constructor_delete_operation", c.getOperationId())
                            .set("constructor_delete_semester_id", c.getScope().getSemesterId())
                            .set("constructor_delete_group_id", c.getScope().getGroupId()), PAIRS);
        } else if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_SEAL) {
            if (!Set.of("PREPARED", "SEALED").contains(epoch.getString("state"))) throw conflict();
            epoch.put("state", "SEALED");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getOperationId())), new Update().set("state", "SEALED"), OPERATIONS);
        } else if (action == ConstructorDeletionAction.CONSTRUCTOR_DELETION_COMMIT) {
            if (!"SEALED".equals(epoch.getString("state"))) throw conflict();
            for (long id : lessons.keySet()) mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(PairWriteCoordinator.lessonFenceId(id)).and("constructor_delete_operation").is(c.getOperationId())),
                    new Update().set("constructor_delete_committed", true), PAIRS);
            for (Row row : current.rows) {
                // Pair fences and receipt history are control state, never a business cascade.
                if (!row.collection.equals(PAIRS)) mongo.remove(Query.query(Criteria.where("_id").is(row.document.get("_id"))), row.collection);
            }
            epoch.put("state", "DELETED");
            mongo.updateFirst(Query.query(Criteria.where("_id").is(c.getOperationId())), new Update().set("state", "DELETED"), OPERATIONS);
            try {
                outbox.save("dashboard.invalidated", mapper.writeValueAsString(ru.rutcampustrack.attendance.event.EventEnvelope.build("dashboard.invalidated", Map.of())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw conflict(); }
        } else throw conflict();
        return receipt(c, epoch);
    }

    private void touchOwnedFences(ConstructorDeletionCommand c, Set<Long> ids, boolean keep) {
        for (long id : ids) {
            Update update = new Update().inc("fence", 1L).set("updated_at", clock.instant());
            if (!keep) update.unset("constructor_delete_operation");
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(PairWriteCoordinator.lessonFenceId(id))
                    .and("constructor_delete_operation").is(c.getOperationId()).and("constructor_delete_committed").ne(true)), update, PAIRS);
            if (result.getMatchedCount() != 1) throw conflict();
        }
    }

    private Snapshot snapshot(ConstructorDeletionCommand c, Map<Long, ConstructorLessonScope> lessons) {
        Set<Long> ids = lessons.keySet();
        List<Row> rows = new ArrayList<>();
        Map<String, Set<Long>> reasons = new TreeMap<>();
        Set<Long> protectedLessons = new TreeSet<>();
        Set<ByteString> allowed = new HashSet<>(c.getScope().getAllowedSystemAuditHashesList());
        for (String collection : List.of("attendances", "late_checkin_requests", "lesson_actions", "lesson_cancellation_markers", "student_checkin_receipts", PAIRS)) {
            for (Document row : mongo.find(Query.query(Criteria.where("lesson_id").in(ids)), Document.class, collection)) {
                long id = number(row.get("lesson_id"));
                if (collection.equals(PAIRS) && PairWriteCoordinator.lessonFenceId(id).equals(row.get("_id"))) continue;
                rows.add(new Row(collection, row));
                String reason = null;
                if (!scope(row, c, collection)) reason = "UNKNOWN_HISTORY";
                else if (collection.equals("attendances")) {
                    String source = knownEnumCode(row.get("source"), ru.rutcampustrack.attendance.contract.enums.AttendanceSource.class);
                    String status = knownEnumCode(row.get("status"), ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.class);
                    if (source == null || status == null) reason = "UNKNOWN_HISTORY";
                    else if (!"AUTO_SCHEDULER".equals(source) || row.get("marked_by") != null
                            || row.get("attachment_id") != null || row.get("excuse_reason") != null
                            || row.get("excuse_type") != null || row.get("excuse_comment") != null
                            || !("ABSENT".equals(status) || "CANCELLED".equals(status))) {
                        reason = source.contains("GEO") ? "GEO" : "MANUAL_ATTENDANCE";
                    }
                } else if (collection.equals("late_checkin_requests")) reason = "REQUEST";
                else if (collection.equals("student_checkin_receipts")) reason = "GEO";
                else if (collection.equals(PAIRS)) {
                    // Auto closure never creates student pair locks. Even a cleared/no-op manual
                    // command leaves this permanent evidence when the current mark is absent.
                    reason = row.get("last_attempt_at") != null || row.get("retry_at") != null ? "GEO" : "USER_ACTION";
                } else if (collection.equals("lesson_actions")) reason = actionReason(row, allowed);
                if (reason != null) protect(reason, Set.of(id), lessons, reasons, protectedLessons);
            }
        }
        // Read BSON, not effective getters: narrowed/archived requests still protect originals.
        List<Criteria> links = new ArrayList<>();
        for (String field : List.of("lesson_ids", "original_lesson_ids", "effective_original_lesson_ids", "lesson_snapshots.lesson_id", "effective_lesson_snapshots.lesson_id", "decision_selection.included_lessons.lesson_id", "decision_selection.excluded_lessons.lesson_id", "effective_decision_selection.included_lessons.lesson_id", "effective_decision_selection.excluded_lessons.lesson_id", "subject_coverage_adjustments.removed_lesson_ids", "subject_coverage_adjustments.before_snapshots.lesson_id", "subject_coverage_adjustments.before_original_ids", "subject_coverage_adjustments.before_decision_selection.included_lessons.lesson_id", "subject_coverage_adjustments.before_decision_selection.excluded_lessons.lesson_id")) links.add(Criteria.where(field).in(ids));
        for (Document row : mongo.find(Query.query(new Criteria().orOperator(links)), Document.class, "excuse_tickets")) {
            rows.add(new Row("excuse_tickets", row));
            Set<Long> affected = new TreeSet<>();
            collectLessonIds(row, null, ids, affected);
            if (affected.isEmpty()) affected.addAll(ids); // unprovable imported coverage fails closed
            protect(scope(row, c, "excuse_tickets") ? "REQUEST" : "UNKNOWN_HISTORY", affected, lessons, reasons, protectedLessons);
        }
        for (Document row : mongo.find(Query.query(new Criteria().orOperator(Criteria.where("source_lesson_id").in(ids), Criteria.where("target_lesson_id").in(ids))), Document.class, "lesson_transfer_fences")) {
            rows.add(new Row("lesson_transfer_fences", row));
            Set<Long> affected = new TreeSet<>();
            for (String key : List.of("source_lesson_id", "target_lesson_id")) if (ids.contains(number(row.get(key)))) affected.add(number(row.get(key)));
            protect("TRANSFER", affected, lessons, reasons, protectedLessons);
        }
        // Legacy mark attachment keys encode the physical lesson even if its mark was cleared.
        for (long id : ids) for (Document row : mongo.find(Query.query(Criteria.where("request_id").regex("^[1-9][0-9]*:" + id + "$")), Document.class, "request_attachments")) {
            Document metadata = new Document(row); metadata.remove("data");
            rows.add(new Row("request_attachments", metadata));
            protect("REQUEST", Set.of(id), lessons, reasons, protectedLessons);
        }
        rows.sort(Comparator.comparing(Row::collection).thenComparing(row -> row.document.get("_id").toString()));
        StringBuilder manifest = new StringBuilder("constructor-attendance-v1\n");
        for (Row row : rows) manifest.append(row.collection).append(':').append(row.document.toJson()).append('\n');
        return new Snapshot(ConstructorHashes.bytes(manifest.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), rows, reasons, protectedLessons);
    }

    private static String actionReason(Document row, Set<ByteString> allowed) {
        Object raw = row.get("payload");
        if (!(raw instanceof Map<?, ?> p) || !(p.get("action") instanceof String action)) return "UNKNOWN_HISTORY";
        if ("AUTO_ABSENT".equals(action) && "attendance-service".equals(row.getString("source"))
                && p.get("actor") == null && p.get("before") == null && p.get("after") instanceof Map<?, ?> after
                && "AUTO_SCHEDULER".equals(after.get("source")) && "ABSENT".equals(after.get("status"))
                && after.get("marked_by") == null && p.get("geo") == null && p.get("ticket") == null && p.get("homework") == null) return null;
        // Cancellation's local projection is system history only when both sides are auto-N.
        // Its originating Schedule command is independently protected/allowlisted in SQL/audit.
        if ("ATTENDANCE_SET".equals(action) && "attendance-service".equals(row.getString("source"))
                && p.get("actor") == null && p.get("before") instanceof Map<?, ?> before && p.get("after") instanceof Map<?, ?> after
                && "AUTO_SCHEDULER".equals(before.get("source")) && "ABSENT".equals(before.get("status"))
                && before.get("marked_by") == null && "AUTO_SCHEDULER".equals(after.get("source"))
                && "CANCELLED".equals(after.get("status")) && after.get("marked_by") == null
                && p.get("geo") == null && p.get("ticket") == null && p.get("homework") == null) return null;
        if ("schedule-service".equals(row.getString("source")) && Set.of("LESSON_CREATED", "LESSON_CANCELLED").contains(action)
                && allowed.contains(ConstructorHashes.hash(p))) return null;
        if (p.get("geo") != null || action.startsWith("GEO") || action.contains("CHECKIN")) return "GEO";
        if (p.get("ticket") != null || action.startsWith("REQUEST")) return "REQUEST";
        if (p.get("homework") != null || action.startsWith("HOMEWORK")) return "HOMEWORK";
        if (action.contains("TRANSFER")) return "TRANSFER";
        if ("LESSON_CANCELLED".equals(action)) return "MANUAL_CANCELLATION";
        if (Set.of("MARK_SET", "MARK_CLEARED", "MARK_CHANGED", "MARK_BATCH", "ATTENDANCE_SET", "ATTENDANCE_CLEARED").contains(action)) return "MANUAL_ATTENDANCE";
        return p.get("actor") != null ? "USER_ACTION" : "UNKNOWN_HISTORY";
    }

    private static void collectLessonIds(Object value, String key, Set<Long> ids, Set<Long> found) {
        if (value instanceof Map<?, ?> map) map.forEach((k,v) -> collectLessonIds(v, k.toString(), ids, found));
        else if (value instanceof Iterable<?> list) list.forEach(v -> collectLessonIds(v, key, ids, found));
        else if (key != null && (key.equals("lesson_id") || key.endsWith("lesson_ids")) && ids.contains(number(value))) found.add(number(value));
    }

    private static boolean scope(Document row, ConstructorDeletionCommand c, String collection) {
        if (Set.of("student_checkin_receipts", "lesson_cancellation_markers").contains(collection)) {
            return !collection.equals("lesson_cancellation_markers") || number(row.get("semester_id")) == c.getScope().getSemesterId();
        }
        if (number(row.get("group_id")) != c.getScope().getGroupId()) return false;
        if (!collection.equals(PAIRS) && number(row.get("semester_id")) != c.getScope().getSemesterId()) return false;
        return row.get("subject_id") == null || number(row.get("subject_id")) == c.getScope().getSubjectId();
    }

    private static void protect(String reason, Set<Long> ids, Map<Long, ConstructorLessonScope> lessons, Map<String, Set<Long>> reasons, Set<Long> all) {
        for (long id : ids) {
            long occurrence = lessons.get(id).getOccurrenceId();
            reasons.computeIfAbsent(reason, ignored -> new TreeSet<>()).add(occurrence); all.add(occurrence);
        }
    }

    private static Map<Long, ConstructorLessonScope> validate(ConstructorDeletionCommand c) {
        try { UUID.fromString(c.getOperationId()); } catch (RuntimeException bad) { throw conflict(); }
        var s = c.getScope();
        if (c.getOriginalActorId() <= 0 || c.getRequestHash().size() != 32 || c.getScopeHash().size() != 32
                || s.getGroupId() <= 0 || s.getSubjectId() <= 0 || s.getSemesterId() <= 0 || s.getScheduleManifestDigest().size() != 32
                || c.getAction() == ConstructorDeletionAction.UNRECOGNIZED || c.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_ACTION_UNSPECIFIED
                || c.getAction() != ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW && c.getParticipantDigest().size() != 32) throw conflict();
        Set<Long> templates = new HashSet<>(s.getScheduleItemIdsList());
        if (templates.isEmpty() || templates.size() != s.getScheduleItemIdsCount() || templates.stream().anyMatch(id -> id <= 0)) throw conflict();
        if (s.getAllowedSystemAuditHashesList().stream().anyMatch(hash -> hash.size() != 32)) throw conflict();
        Map<Long, ConstructorLessonScope> lessons = new TreeMap<>();
        for (var l : s.getLessonsList()) {
            if (l.getLessonId() <= 0 || l.getOccurrenceId() <= 0 || !templates.contains(l.getScheduleItemId()) || l.getGeneration() <= 0
                    || l.getLessonRevision() <= 0 || l.getOccurrenceRevision() <= 0 || l.getLessonNumber() <= 0 || l.getLessonType().isBlank()
                    || l.getOriginProvenanceHash().size() != 32 || lessons.put(l.getLessonId(), l) != null) throw conflict();
            try { LocalDate.parse(l.getLessonDate()); } catch (RuntimeException bad) { throw conflict(); }
        }
        return lessons;
    }

    private static String identity(ConstructorDeletionCommand c) {
        return HexFormat.of().formatHex(ConstructorHashes.bytes(c.toBuilder().setAction(ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREPARE).build().toByteArray()).toByteArray());
    }
    private static long number(Object value) { return value instanceof Number n && n.doubleValue() == n.longValue() ? n.longValue() : 0; }
    private static <E extends Enum<E>> String knownEnumCode(Object value, Class<E> type) {
        if (!(value instanceof String code)) return null;
        try { return Enum.valueOf(type, code.toUpperCase(Locale.ROOT)).name(); }
        catch (IllegalArgumentException unknown) { return null; }
    }
    private static IllegalStateException conflict() { return new IllegalStateException("Constructor deletion scope or history conflict"); }
    private static Document epoch(ConstructorDeletionCommand c, String state) {
        return new Document("_id", c.getOperationId()).append("identity", identity(c)).append("state", state)
                .append("command", c.toByteArray()).append("digest", c.getParticipantDigest().toByteArray());
    }
    private static ConstructorParticipantReceipt receipt(ConstructorDeletionCommand c, Document epoch) {
        return ConstructorParticipantReceipt.newBuilder().setOperationId(c.getOperationId()).setRequestHash(c.getRequestHash())
                .setScopeHash(c.getScopeHash()).setParticipantDigest(c.getParticipantDigest()).setState(epoch.getString("state")).build();
    }
    private record Row(String collection, Document document) {}
    private record Snapshot(ByteString digest, List<Row> rows, Map<String, Set<Long>> reasons, Set<Long> protectedLessons) {
        ConstructorParticipantReceipt receipt(ConstructorDeletionCommand c, String state) {
            var b = ConstructorParticipantReceipt.newBuilder().setOperationId(c.getOperationId()).setRequestHash(c.getRequestHash())
                    .setScopeHash(c.getScopeHash()).setParticipantDigest(digest).setState(state).setProtectedCount(protectedLessons.size());
            reasons.forEach((code, ids) -> b.addProtectedReasons(ConstructorProtectedReason.newBuilder().setCode(code).setCount(ids.size())));
            return b.build();
        }
    }
}
