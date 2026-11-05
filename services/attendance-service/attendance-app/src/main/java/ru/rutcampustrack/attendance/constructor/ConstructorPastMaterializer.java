package ru.rutcampustrack.attendance.constructor;

import java.util.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.attendance.event.LessonEventService;
import ru.rutcampustrack.attendance.student.PairWriteCoordinator;
import ru.rutcampustrack.constructor.grpc.*;

/** Durable direct closure; remote canonical reads precede all Mongo transactions/fences. */
@Service
public class ConstructorPastMaterializer {
    static final String RECEIPTS = "constructor_past_materialization_receipts";
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    private final LessonEventService lessons;
    private final PairWriteCoordinator pairs;

    public ConstructorPastMaterializer(MongoTemplate mongo, TransactionTemplate transactions, LessonEventService lessons, PairWriteCoordinator pairs) {
        this.mongo = mongo; this.transactions = transactions; this.lessons = lessons; this.pairs = pairs;
    }

    public PastLessonMaterializationReceipt apply(PastLessonMaterializationCommand c) {
        validate(c);
        String id = c.getOperationId() + ":" + c.getBatchIndex();
        String identity = HexFormat.of().formatHex(ConstructorHashes.bytes(c.toByteArray()).toByteArray());
        Document prior = mongo.findById(id, Document.class, RECEIPTS);
        if (prior != null && !identity.equals(prior.getString("identity"))) throw conflict();
        Map<Long, String> saved = results(prior);
        Map<Long, LessonEventService.LessonClosedSnapshot> prepared = new TreeMap<>();
        Map<Long, String> unavailable = new TreeMap<>();
        for (PastLessonIdentity target : c.getLessonsList()) {
            if (terminal(saved.get(target.getLessonId()))) continue;
            if (pairs.isSubjectDeleted(target.getLessonId(), c.getSemesterId(), c.getGroupId())) {
                unavailable.put(target.getLessonId(), "TOMBSTONED"); continue;
            }
            try {
                var snapshot = lessons.prepareLessonClosed(target.getLessonId(), c.getGroupId(), c.getSemesterId());
                if (snapshot.lesson() == null) { unavailable.put(target.getLessonId(), "TOMBSTONED"); continue; }
                var lesson = snapshot.lesson();
                if (lesson.getOccurrenceId() != target.getOccurrenceId() || lesson.getRevision() < target.getExpectedRevision()
                        || !ConstructorHashes.lesson(lesson).equals(target.getSnapshotHash())) throw conflict();
                if (Set.of("cancelled", "transferred").contains(lesson.getStatus().toLowerCase(Locale.ROOT))) {
                    unavailable.put(target.getLessonId(), "UNCHANGED"); continue;
                }
                if (!"closed".equalsIgnoreCase(lesson.getStatus())) throw conflict();
                prepared.put(target.getLessonId(), snapshot);
            } catch (ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException coverage) {
                unavailable.put(target.getLessonId(), "COVERAGE_UNAVAILABLE");
            }
        }
        return transactions.execute(status -> {
            Document current = mongo.findById(id, Document.class, RECEIPTS);
            if (current != null && !identity.equals(current.getString("identity"))) throw conflict();
            Map<Long, String> result = results(current);
            // Acquire all lesson fences in the global sorted order before nested closures.
            pairs.lockAcceptedScheduleEffectLessons(c.getSemesterId(), c.getLessonsList().stream().map(PastLessonIdentity::getLessonId).toList(), c.getGroupId(), java.time.Instant.now());
            for (PastLessonIdentity target : c.getLessonsList()) {
                long lessonId = target.getLessonId();
                if (pairs.isSubjectDeleted(lessonId, c.getSemesterId(), c.getGroupId())) {
                    result.put(lessonId, "TOMBSTONED"); continue;
                }
                if (terminal(result.get(lessonId))) continue;
                var snapshot = prepared.get(lessonId);
                if (snapshot == null) { result.put(lessonId, unavailable.get(lessonId)); continue; }
                long before = mongo.count(Query.query(Criteria.where("lesson_id").is(lessonId)), "attendances");
                lessons.applyLessonClosed(snapshot);
                long after = mongo.count(Query.query(Criteria.where("lesson_id").is(lessonId)), "attendances");
                result.put(lessonId, snapshot.members().getStudentsCount() == 0 ? "NO_ELIGIBLE_MEMBERS" : after > before ? "APPLIED" : "UNCHANGED");
            }
            var receipt = PastLessonMaterializationReceipt.newBuilder().setOperationId(c.getOperationId()).setBatchIndex(c.getBatchIndex()).setBatchHash(c.getBatchHash());
            List<Document> entries = new ArrayList<>();
            for (var target : c.getLessonsList()) {
                String state = result.get(target.getLessonId());
                if (state == null) throw conflict();
                receipt.addLessons(PastLessonMaterializationResult.newBuilder().setLessonId(target.getLessonId()).setState(state));
                entries.add(new Document("lesson_id", target.getLessonId()).append("state", state));
            }
            if (current == null) mongo.insert(new Document("_id", id).append("identity", identity).append("results", entries), RECEIPTS);
            else mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("results", entries), RECEIPTS);
            return receipt.build();
        });
    }

    private static boolean terminal(String state) { return state != null && !"COVERAGE_UNAVAILABLE".equals(state); }
    private static Map<Long, String> results(Document row) {
        Map<Long, String> result = new TreeMap<>();
        if (row == null) return result;
        for (Document item : row.getList("results", Document.class)) result.put(((Number)item.get("lesson_id")).longValue(), item.getString("state"));
        return result;
    }
    private static void validate(PastLessonMaterializationCommand c) {
        try { UUID.fromString(c.getOperationId()); } catch (RuntimeException invalid) { throw conflict(); }
        if (c.getGroupId() <= 0 || c.getSemesterId() <= 0 || c.getBatchCount() <= 0 || c.getBatchIndex() < 0 || c.getBatchIndex() >= c.getBatchCount()
                || c.getBatchHash().size() != 32 || c.getLessonsCount() == 0 || c.getLessonsCount() > 128) throw conflict();
        Set<Long> ids = new HashSet<>();
        for (var l : c.getLessonsList()) if (l.getLessonId() <= 0 || l.getOccurrenceId() <= 0 || l.getExpectedRevision() <= 0 || l.getSnapshotHash().size() != 32 || !ids.add(l.getLessonId())) throw conflict();
    }
    private static IllegalStateException conflict() { return new IllegalStateException("Constructor materialization identity conflict"); }
}
