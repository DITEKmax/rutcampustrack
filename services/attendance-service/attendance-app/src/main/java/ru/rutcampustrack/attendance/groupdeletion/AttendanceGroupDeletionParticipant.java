package ru.rutcampustrack.attendance.groupdeletion;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.groupdeletion.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AttendanceGroupDeletionParticipant {
    private static final String EPOCHS = "group_deletion_participants";
    private final MongoTemplate mongo;
    private final GroupDeletionMongoFence fences;

    public AttendanceGroupDeletionParticipant(MongoTemplate mongo, GroupDeletionMongoFence fences) {
        this.mongo = mongo;
        this.fences = fences;
    }

    private record Row(String collection, Document value) {}

    private record Snapshot(String digest, List<Row> rows, Map<String, Long> counts) {}

    @Transactional(transactionManager = "mongoTransactionManager")
    public GroupDeletionReceipt apply(GroupDeletionCommand c) {
        validate(c);
        if (c.getAction() == GroupDeletionAction.PREVIEW) return receipt(c, snapshot(c));
        Document epoch = mongo.findById(c.getOperationId(), Document.class, EPOCHS);
        if (epoch != null && !epoch.getString("identity").equals(identity(c))) throw conflict();
        if (epoch != null && "DELETED".equals(epoch.getString("state"))) {
            if (c.getAction() != GroupDeletionAction.COMMIT) throw conflict();
            return stored(c, epoch);
        }
        if (epoch != null && "RELEASED".equals(epoch.getString("state"))) {
            if (c.getAction() != GroupDeletionAction.RELEASE) throw conflict();
            return stored(c, epoch);
        }
        Document fence = fences.touch(c.getGroupId());
        if (fence.getString("operation") != null
                && !fence.getString("operation").equals(c.getOperationId())) throw conflict();
        if (c.getAction() == GroupDeletionAction.RELEASE) {
            if (epoch == null)
                mongo.insert(
                        new Document("_id", c.getOperationId())
                                .append("identity", identity(c))
                                .append("state", "RELEASED")
                                .append("digest", c.getParticipantDigest())
                                .append("counts", new Document()),
                        EPOCHS);
            else
                mongo.updateFirst(
                        Query.query(Criteria.where("_id").is(c.getOperationId())),
                        new Update().set("state", "RELEASED"),
                        EPOCHS);
            mongo.remove(
                    Query.query(Criteria.where("operation").is(c.getOperationId())),
                    GroupDeletionMongoFence.SCOPES);
            mongo.updateFirst(
                    Query.query(
                            Criteria.where("_id")
                                    .is(Long.toString(c.getGroupId()))
                                    .and("operation")
                                    .is(c.getOperationId())),
                    new Update().unset("operation"),
                    GroupDeletionMongoFence.FENCES);
            return epoch == null
                    ? GroupDeletionReceipt.newBuilder()
                            .setCommand(c)
                            .setParticipantDigest(c.getParticipantDigest())
                            .build()
                    : stored(c, epoch);
        }
        if (epoch != null && "RELEASED".equals(epoch.getString("state"))) throw conflict();
        if (epoch != null && c.getAction() == GroupDeletionAction.PREPARE) return stored(c, epoch);
        if (c.getAction() == GroupDeletionAction.PREPARE) {
            for (var lesson : c.getLessonsList()) {
                String key = Long.toString(lesson.getLessonId());
                Document old = mongo.findById(key, Document.class, GroupDeletionMongoFence.SCOPES);
                if (old != null
                        && (!Objects.equals(old.get("group_id"), c.getGroupId())
                                || !Objects.equals(old.get("semester_id"), lesson.getSemesterId())
                                || !Objects.equals(old.get("operation"), c.getOperationId())))
                    throw conflict();
                if (old == null)
                    mongo.insert(
                            new Document("_id", key)
                                    .append("group_id", c.getGroupId())
                                    .append("semester_id", lesson.getSemesterId())
                                    .append("operation", c.getOperationId()),
                            GroupDeletionMongoFence.SCOPES);
            }
        }
        Snapshot current = snapshot(c);
        if (!current.digest().equals(c.getParticipantDigest())) throw conflict();
        if (c.getAction() == GroupDeletionAction.PREPARE) {
            if (epoch == null)
                mongo.insert(
                        new Document("_id", c.getOperationId())
                                .append("identity", identity(c))
                                .append("group_id", c.getGroupId())
                                .append("digest", current.digest())
                                .append("counts", new Document(current.counts()))
                                .append("state", "PREPARED"),
                        EPOCHS);
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(Long.toString(c.getGroupId()))),
                    new Update().set("operation", c.getOperationId()),
                    GroupDeletionMongoFence.FENCES);
        } else if (c.getAction() == GroupDeletionAction.SEAL) {
            if (epoch == null || !List.of("PREPARED", "SEALED").contains(epoch.getString("state")))
                throw conflict();
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(c.getOperationId())),
                    new Update().set("state", "SEALED"),
                    EPOCHS);
        } else if (c.getAction() == GroupDeletionAction.COMMIT) {
            if (epoch == null || !"SEALED".equals(epoch.getString("state"))) throw conflict();
            Map<Long, Long> tombstones = new TreeMap<>();
            for (var lesson : c.getLessonsList())
                tombstones.put(lesson.getLessonId(), lesson.getSemesterId());
            for (Row row : current.rows()) {
                Object id = row.value().get("lesson_id");
                Object semester = row.value().get("semester_id");
                if (semester == null) semester = row.value().get("subject_delete_semester_id");
                if (id instanceof Number lesson
                        && semester instanceof Number term
                        && lesson.longValue() > 0
                        && term.longValue() > 0) {
                    Long prior = tombstones.putIfAbsent(lesson.longValue(), term.longValue());
                    if (prior != null && prior != term.longValue()) throw conflict();
                }
            }
            for (var lesson : tombstones.entrySet()) {
                String key = Long.toString(lesson.getKey());
                Document old = mongo.findById(key, Document.class, "group_deletion_lessons");
                if (old != null
                        && (!Objects.equals(old.get("group_id"), c.getGroupId())
                                || !Objects.equals(old.get("semester_id"), lesson.getValue())))
                    throw conflict();
                if (old == null)
                    mongo.insert(
                            new Document("_id", key)
                                    .append("group_id", c.getGroupId())
                                    .append("semester_id", lesson.getValue())
                                    .append("operation", c.getOperationId()),
                            "group_deletion_lessons");
            }
            // The participant alone owns this raw deletion, after exact digest/epoch and
            // transactional
            // group-fence checks. Normal template/repository writers always pass the group fence
            // aspect.
            for (Row row : current.rows())
                mongo.execute(
                        row.collection(),
                        collection ->
                                collection.deleteOne(new Document("_id", row.value().get("_id"))));
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(c.getOperationId())),
                    new Update().set("state", "DELETED"),
                    EPOCHS);
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(Long.toString(c.getGroupId()))),
                    new Update().set("deleted", true),
                    GroupDeletionMongoFence.FENCES);
        } else throw conflict();
        return receipt(c, current);
    }

    private Snapshot snapshot(GroupDeletionCommand command) {
        long group = command.getGroupId();
        List<Row> rows = new ArrayList<>();
        Map<String, Long> counts = new TreeMap<>();
        Map<Long, Long> lessons = new TreeMap<>();
        for (var lesson : command.getLessonsList())
            lessons.put(lesson.getLessonId(), lesson.getSemesterId());
        for (String collection : new TreeSet<>(GroupDeletionMongoFence.COLLECTIONS)) {
            var selected =
                    mongo.find(
                            Query.query(
                                    new Criteria()
                                            .orOperator(
                                                    Criteria.where("group_id").is(group),
                                                    Criteria.where("lesson_id")
                                                            .in(lessons.keySet()))),
                            Document.class,
                            collection);
            counts.put(collection, (long) selected.size());
            for (Document value : selected) {
                if (value.get("group_id") instanceof Number owner && owner.longValue() != group)
                    throw conflict();
                if (value.get("lesson_id") instanceof Number lesson
                        && lessons.containsKey(lesson.longValue())
                        && value.get("semester_id") instanceof Number term
                        && term.longValue() != lessons.get(lesson.longValue())) throw conflict();
                rows.add(new Row(collection, value));
            }
        }
        rows.sort(
                Comparator.comparing(Row::collection)
                        .thenComparing(row -> row.value().get("_id").toString()));
        StringBuilder digest =
                new StringBuilder("group-attendance-v1:")
                        .append(group)
                        .append(command.getLessonsList());
        for (Row row : rows)
            digest.append(':').append(row.collection()).append(':').append(row.value().toJson());
        return new Snapshot(hash(digest.toString()), rows, counts);
    }

    private static GroupDeletionReceipt receipt(GroupDeletionCommand c, Snapshot s) {
        return GroupDeletionReceipt.newBuilder()
                .setCommand(c)
                .setParticipantDigest(s.digest())
                .putAllCounts(s.counts())
                .build();
    }

    private static GroupDeletionReceipt stored(GroupDeletionCommand c, Document epoch) {
        Map<String, Long> counts = new TreeMap<>();
        epoch.get("counts", Document.class)
                .forEach((key, value) -> counts.put(key, ((Number) value).longValue()));
        return GroupDeletionReceipt.newBuilder()
                .setCommand(c)
                .setParticipantDigest(epoch.getString("digest"))
                .putAllCounts(counts)
                .build();
    }

    private static String identity(GroupDeletionCommand c) {
        return hash(c.toBuilder().setAction(GroupDeletionAction.PREPARE).build().toString());
    }

    private static String hash(String s) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    private static void validate(GroupDeletionCommand c) {
        if (c.getGroupId() <= 0
                || c.getActorId() <= 0
                || !c.getUnknownFields().asMap().isEmpty()
                || !UUID.fromString(c.getOperationId()).toString().equals(c.getOperationId())
                || c.getAction() == GroupDeletionAction.UNRECOGNIZED
                || c.getAction() == GroupDeletionAction.GROUP_DELETION_ACTION_UNSPECIFIED)
            throw conflict();
        Set<Long> ids = new HashSet<>();
        for (var lesson : c.getLessonsList())
            if (lesson.getLessonId() <= 0
                    || lesson.getSemesterId() <= 0
                    || !ids.add(lesson.getLessonId())
                    || !lesson.getUnknownFields().asMap().isEmpty()) throw conflict();
        if (c.getAction() != GroupDeletionAction.PREVIEW
                && (!c.getParticipantDigest().matches("[0-9a-f]{64}")
                        || !c.getPreviewDigest().matches("[0-9a-f]{64}"))) throw conflict();
    }

    private static RuntimeException conflict() {
        return io.grpc.Status.FAILED_PRECONDITION
                .withDescription("Group deletion exact scope or phase changed")
                .asRuntimeException();
    }
}
