package ru.rutcampustrack.attendance.subjectdeletion;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.attendance.audit.LessonActionService;
import ru.rutcampustrack.attendance.audit.RequestActionRecorder;
import ru.rutcampustrack.attendance.event.EventEnvelope;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;

/** Mongo participant: exact marks/sole tickets are removed; mixed coverage and quota remain. */
@Service
public class AttendanceSubjectDeletionParticipant {
    private static final String EPOCHS = "subject_deletion_participants";
    private final MongoTemplate mongo;
    private final PairWriteCoordinator pairs;
    private final LessonActionService actions;
    private final OutboxStorage outbox;
    private final ObjectMapper mapper;
    private final Clock clock;

    public AttendanceSubjectDeletionParticipant(
            MongoTemplate mongo,
            PairWriteCoordinator pairs,
            LessonActionService actions,
            OutboxStorage outbox,
            ObjectMapper mapper,
            Clock clock) {
        this.mongo = mongo;
        this.pairs = pairs;
        this.actions = actions;
        this.outbox = outbox;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(transactionManager = "mongoTransactionManager")
    public SubjectDeletionReceipt apply(SubjectDeletionCommand command) {
        Set<Long> ids = validate(command);
        if (command.getAction() == SubjectDeletionAction.PREVIEW)
            return snapshot(command, ids).receipt(command);
        Document epoch = mongo.findById(command.getOperationId(), Document.class, EPOCHS);
        if (epoch != null) {
            if (!Objects.equals(epoch.getString("identity"), identity(command))) throw conflict();
            String state = epoch.getString("state");
            if ("DELETED".equals(state)) {
                if (command.getAction() != SubjectDeletionAction.COMMIT) throw conflict();
                return savedReceipt(command, epoch);
            }
            if ("RELEASED".equals(state) && command.getAction() != SubjectDeletionAction.RELEASE)
                throw conflict();
            if (command.getAction() == SubjectDeletionAction.PREPARE)
                return savedReceipt(command, epoch);
        }
        if (command.getAction() == SubjectDeletionAction.RELEASE) {
            if (epoch != null) {
                for (long id : fenceIds(epoch))
                    mongo.updateFirst(
                            Query.query(
                                    Criteria.where("_id")
                                            .is(PairWriteCoordinator.lessonFenceId(id))
                                            .and("subject_delete_operation")
                                            .is(command.getOperationId())),
                            new Update().unset("subject_delete_operation"),
                            "student_checkin_pairs");
                mongo.updateFirst(
                        Query.query(Criteria.where("_id").is(command.getOperationId())),
                        new Update().set("state", "RELEASED"),
                        EPOCHS);
            }
            return epoch == null
                    ? SubjectDeletionReceipt.newBuilder()
                            .setOperationId(command.getOperationId())
                            .setSubjectId(command.getSubjectId())
                            .setGroupId(command.getGroupId())
                            .setSemesterId(command.getSemesterId())
                            .setParticipantDigest(command.getParticipantDigest())
                            .setAction(command.getAction())
                            .setManifestDigest(command.getManifestDigest())
                            .setActorId(command.getActorId())
                            .build()
                    : savedReceipt(command, epoch);
        }
        if (epoch == null && command.getAction() != SubjectDeletionAction.PREPARE) throw conflict();
        if (epoch == null) {
            pairs.lockLessons(command.getSemesterId(), ids, command.getGroupId(), clock.instant());
            if (mongo.exists(
                    Query.query(
                            Criteria.where("subject_id")
                                    .is(command.getSubjectId())
                                    .and("state")
                                    .nin("RELEASED")),
                    EPOCHS)) throw conflict();
        }
        Snapshot current = snapshot(command, ids);
        if (!current.digest().equals(command.getParticipantDigest())) throw conflict();
        if (command.getAction() == SubjectDeletionAction.PREPARE) {
            Set<Long> shared = new TreeSet<>(ids);
            // A submitted mixed ticket can also be changed by a transfer of its OTHER lesson.
            // Serialize those real mutation entrypoints before persisting the sealed digest.
            for (Row row : current.entries()) {
                if (!row.collection().equals("excuse_tickets")) continue;
                ExcuseTicket ticket = mongo.getConverter().read(ExcuseTicket.class, row.document());
                shared.addAll(ExcuseSubjectCoverage.originals(ticket));
                if (ticket.getLessonIds() != null) shared.addAll(ticket.getLessonIds());
                if (ticket.getLessonSnapshots() != null)
                    ticket.getLessonSnapshots()
                            .forEach(snapshot -> shared.add(snapshot.getLessonId()));
            }
            pairs.lockLessons(
                    command.getSemesterId(), shared, command.getGroupId(), clock.instant());
            epoch =
                    new Document("_id", command.getOperationId())
                            .append("identity", identity(command))
                            .append("subject_id", command.getSubjectId())
                            .append("semester_id", command.getSemesterId())
                            .append("group_id", command.getGroupId())
                            .append("state", "PREPARED")
                            .append("rows", current.rows())
                            .append("mixed", current.mixed())
                            .append("fence_ids", List.copyOf(shared))
                            .append("digest", current.digest());
            mongo.insert(epoch, EPOCHS);
            for (long id : shared)
                mongo.updateFirst(
                        Query.query(
                                Criteria.where("_id").is(PairWriteCoordinator.lessonFenceId(id))),
                        new Update().set("subject_delete_operation", command.getOperationId()),
                        "student_checkin_pairs");
        } else if (command.getAction() == SubjectDeletionAction.SEAL) {
            if (!List.of("PREPARED", "SEALED").contains(epoch.getString("state"))) throw conflict();
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(command.getOperationId())),
                    new Update().set("state", "SEALED"),
                    EPOCHS);
        } else if (command.getAction() == SubjectDeletionAction.COMMIT) {
            if (!"SEALED".equals(epoch.getString("state"))) throw conflict();
            // Writes the same epoch and lesson fence documents as other attempts before any
            // effects.
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(command.getOperationId())),
                    new Update().set("state", "DELETED"),
                    EPOCHS);
            for (long id : ids) {
                var changed =
                        mongo.updateFirst(
                                Query.query(
                                        Criteria.where("_id")
                                                .is(PairWriteCoordinator.lessonFenceId(id))
                                                .and("subject_delete_operation")
                                                .is(command.getOperationId())),
                                new Update()
                                        .inc("fence", 1L)
                                        .set("subject_delete_committed", true)
                                        .set("subject_delete_semester_id", command.getSemesterId()),
                                "student_checkin_pairs");
                if (changed.getMatchedCount() != 1) throw conflict();
            }
            for (Row row : current.entries()) {
                if (row.collection().equals("excuse_tickets")) {
                    ExcuseTicket ticket =
                            mongo.getConverter().read(ExcuseTicket.class, row.document());
                    if (ExcuseSubjectCoverage.originals(ticket).stream()
                            .anyMatch(id -> !ids.contains(id))) {
                        Map<String, Object> before = RequestActionRecorder.snapshot(ticket);
                        if (!ExcuseSubjectCoverage.subtract(
                                ticket,
                                ids,
                                command.getSubjectId(),
                                command.getOperationId(),
                                command.getActorId(),
                                clock.instant())) throw conflict();
                        mongo.save(ticket);
                        actions.local(
                                ticket.getGroupId(),
                                ticket.getSemesterId(),
                                ExcuseSubjectCoverage.originals(ticket).getFirst(),
                                "REQUEST_COVERAGE_CHANGED",
                                LessonActionService.person(command.getActorId(), "HEADMAN", null),
                                LessonActionService.person(
                                        ticket.getStudentId(), "STUDENT", ticket.getStudentName()),
                                before,
                                RequestActionRecorder.snapshot(ticket),
                                null,
                                RequestActionRecorder.reference(
                                        "EXCUSE", ticket.getId(), ticket.getStatus().name()),
                                clock.instant());
                        continue;
                    }
                }
                mongo.remove(
                        Query.query(Criteria.where("_id").is(row.document().get("_id"))),
                        row.collection());
            }
            // Other-subject lessons were fenced only because they share a mutable ticket.
            // Release them atomically AFTER its effective coverage/history was saved.
            for (long id : fenceIds(epoch)) {
                if (ids.contains(id)) continue;
                mongo.updateFirst(
                        Query.query(
                                Criteria.where("_id")
                                        .is(PairWriteCoordinator.lessonFenceId(id))
                                        .and("subject_delete_operation")
                                        .is(command.getOperationId())),
                        new Update().unset("subject_delete_operation"),
                        "student_checkin_pairs");
            }
            try {
                outbox.save(
                        "dashboard.invalidated",
                        mapper.writeValueAsString(
                                EventEnvelope.build("dashboard.invalidated", Map.of())));
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                throw conflict();
            }
        } else throw conflict();
        return current.receipt(command);
    }

    private Snapshot snapshot(SubjectDeletionCommand c, Set<Long> ids) {
        List<Row> rows = new ArrayList<>();
        for (String collection : List.of("attendances", "late_checkin_requests")) {
            for (Document row :
                    mongo.find(
                            Query.query(
                                    new Criteria()
                                            .orOperator(
                                                    Criteria.where("lesson_id").in(ids),
                                                    Criteria.where("subject_id")
                                                            .is(c.getSubjectId()))),
                            Document.class,
                            collection)) {
                scope(row, c);
                if (!ids.contains(number(row.get("lesson_id")))) throw conflict();
                rows.add(new Row(collection, row));
            }
        }
        Map<String, Long> attachmentOwners = new HashMap<>();
        long mixed = 0;
        for (Document document :
                mongo.find(
                        Query.query(
                                new Criteria()
                                        .orOperator(
                                                Criteria.where("lesson_ids").in(ids),
                                                Criteria.where("lesson_snapshots.subject_id")
                                                        .is(c.getSubjectId()))),
                        Document.class,
                        "excuse_tickets")) {
            ExcuseTicket ticket = mongo.getConverter().read(ExcuseTicket.class, document);
            if (ticket.getLessonSnapshots() != null
                    && ticket.getLessonSnapshots().stream()
                            .anyMatch(
                                    snapshot ->
                                            Objects.equals(
                                                            snapshot.getSubjectId(),
                                                            c.getSubjectId())
                                                    && !ids.contains(snapshot.getLessonId())))
                throw conflict();
            if (!ExcuseSubjectCoverage.affects(ticket, ids)) continue;
            scope(document, c);
            boolean remaining =
                    ExcuseSubjectCoverage.originals(ticket).stream()
                            .anyMatch(id -> !ids.contains(id));
            if (remaining) {
                // Validate the real prospective subtraction during preview/SEAL as well.
                ExcuseSubjectCoverage.subtract(
                        ticket,
                        ids,
                        c.getSubjectId(),
                        c.getOperationId(),
                        c.getActorId(),
                        clock.instant());
                mixed++;
            } else attachmentOwners.put(ticket.getId(), ticket.getStudentId());
            rows.add(new Row("excuse_tickets", document));
        }
        for (Document attachment :
                mongo.find(
                        Query.query(
                                new Criteria()
                                        .orOperator(
                                                Criteria.where("group_id")
                                                        .is(c.getGroupId())
                                                        .and("semester_id")
                                                        .is(c.getSemesterId()),
                                                Criteria.where("request_id")
                                                        .in(attachmentOwners.keySet()))),
                        Document.class,
                        "request_attachments")) {
            String request = attachment.getString("request_id");
            Long owner = number(attachment.get("owner_student_id"));
            if (request != null
                    && attachmentOwners.containsKey(request)
                    && !Objects.equals(attachmentOwners.get(request), owner)) throw conflict();
            boolean selected =
                    request != null
                            && attachmentOwners.containsKey(request)
                            && Objects.equals(attachmentOwners.get(request), owner);
            if (request != null && request.matches("[1-9][0-9]*:[1-9][0-9]*")) {
                String[] parts = request.split(":");
                try {
                    selected =
                            ids.contains(Long.parseLong(parts[1]))
                                    && Objects.equals(owner, Long.parseLong(parts[0]));
                } catch (NumberFormatException invalid) {
                    throw conflict();
                }
            }
            if (!selected) continue;
            scope(attachment, c);
            if (owner == null
                    || owner <= 0
                    || !(attachment.get("sha256") instanceof String hash)
                    || !hash.matches("[0-9a-f]{64}")) throw conflict();
            Document metadata = new Document(attachment);
            metadata.remove("data");
            metadata.remove("state");
            metadata.remove("expired_at");
            rows.add(new Row("request_attachments", metadata));
        }
        rows.sort(
                Comparator.comparing(Row::collection)
                        .thenComparing(row -> row.document().get("_id").toString()));
        StringBuilder digest = new StringBuilder("subject-attendance-v1\n");
        for (Row row : rows)
            digest.append(row.collection())
                    .append(':')
                    .append(row.document().toJson())
                    .append('\n');
        return new Snapshot(hash(digest.toString()), List.copyOf(rows), rows.size(), mixed);
    }

    private static void scope(Document row, SubjectDeletionCommand c) {
        if (!Objects.equals(number(row.get("group_id")), c.getGroupId())
                || !Objects.equals(number(row.get("semester_id")), c.getSemesterId()))
            throw conflict();
        Long subject = number(row.get("subject_id"));
        if (subject != null && subject != c.getSubjectId()) throw conflict();
    }

    private static Set<Long> validate(SubjectDeletionCommand c) {
        UUID.fromString(c.getOperationId());
        if (c.getSubjectId() <= 0
                || c.getSemesterId() <= 0
                || c.getGroupId() <= 0
                || c.getActorId() <= 0
                || c.getAction() == SubjectDeletionAction.UNRECOGNIZED
                || c.getAction() == SubjectDeletionAction.SUBJECT_DELETION_ACTION_UNSPECIFIED)
            throw conflict();
        Set<Long> ids = new TreeSet<>();
        for (var lesson : c.getLessonsList()) {
            if (lesson.getLessonId() <= 0
                    || lesson.getSubjectId() != c.getSubjectId()
                    || lesson.getGroupId() != c.getGroupId()
                    || lesson.getSemesterId() != c.getSemesterId()
                    || !ids.add(lesson.getLessonId())) throw conflict();
        }
        if (c.getAction() != SubjectDeletionAction.PREVIEW
                && (!c.getParticipantDigest().matches("[0-9a-f]{64}")
                        || !c.getManifestDigest().matches("[0-9a-f]{64}"))) throw conflict();
        return ids;
    }

    private static SubjectDeletionReceipt savedReceipt(SubjectDeletionCommand c, Document epoch) {
        return SubjectDeletionReceipt.newBuilder()
                .setOperationId(c.getOperationId())
                .setSubjectId(c.getSubjectId())
                .setSemesterId(c.getSemesterId())
                .setGroupId(c.getGroupId())
                .setParticipantDigest(epoch.getString("digest"))
                .setAction(c.getAction())
                .setManifestDigest(c.getManifestDigest())
                .setActorId(c.getActorId())
                .setAffectedRows(((Number) epoch.get("rows")).longValue())
                .setMixedTickets(((Number) epoch.get("mixed")).longValue())
                .build();
    }

    private static String identity(SubjectDeletionCommand c) {
        return hash(c.toBuilder().setAction(SubjectDeletionAction.PREPARE).build().toString());
    }

    private static Long number(Object raw) {
        return raw instanceof Number n ? n.longValue() : null;
    }

    private static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private static Set<Long> fenceIds(Document epoch) {
        Object saved = epoch.get("fence_ids");
        if (!(saved instanceof List<?> values) || values.isEmpty()) throw conflict();
        Set<Long> ids = new TreeSet<>();
        for (Object value : values) {
            Long id = number(value);
            if (id == null || id <= 0 || !ids.add(id)) throw conflict();
        }
        return ids;
    }

    private static IllegalStateException conflict() {
        return new IllegalStateException("Subject deletion scope or phase conflict");
    }

    private record Row(String collection, Document document) {}

    private record Snapshot(String digest, List<Row> entries, long rows, long mixed) {
        SubjectDeletionReceipt receipt(SubjectDeletionCommand c) {
            return SubjectDeletionReceipt.newBuilder()
                    .setOperationId(c.getOperationId())
                    .setSubjectId(c.getSubjectId())
                    .setSemesterId(c.getSemesterId())
                    .setGroupId(c.getGroupId())
                    .setParticipantDigest(digest)
                    .setAction(c.getAction())
                    .setManifestDigest(c.getManifestDigest())
                    .setActorId(c.getActorId())
                    .setAffectedRows(rows)
                    .setMixedTickets(mixed)
                    .build();
        }
    }
}
