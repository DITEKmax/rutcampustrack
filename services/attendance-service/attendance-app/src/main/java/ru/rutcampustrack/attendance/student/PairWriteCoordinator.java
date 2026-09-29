package ru.rutcampustrack.attendance.student;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.event.LessonTransferFenceDocument;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Serializes every attendance mutation for one student/lesson pair. Callers must
 * invoke this inside the same Mongo transaction as their domain writes and then
 * re-read the attendance/request state.
 */
@Component
public class PairWriteCoordinator {

    private final MongoTemplate mongoTemplate;

    public PairWriteCoordinator(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public CheckinPairStateDocument lock(long studentId, long lessonId, long groupId, Instant now) {
        lockLessons(List.of(lessonId), groupId, now);
        if (isTransferredSource(lessonId)) {
            throw new ConflictException("Урок перенесён; обнови данные и повтори действие");
        }
        String id = pairId(studentId, lessonId);
        Query query = Query.query(Criteria.where("_id").is(id));
        Update update = new Update()
                .inc("fence", 1L)
                .set("updated_at", now)
                .setOnInsert("student_id", studentId)
                .setOnInsert("lesson_id", lessonId)
                .setOnInsert("group_id", groupId);
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                CheckinPairStateDocument.class
        );
    }

    /**
     * Fences every attendance mutation for the listed physical lessons. The
     * operation lock shares the collection with pair locks, so a transfer and
     * a writer conflict inside Mongo's transaction boundary even when a mark
     * for a new student would not yet have a pair document.
     */
    public void lockLessons(Collection<Long> lessonIds, Long groupId, Instant now) {
        if (lessonIds == null || lessonIds.isEmpty()) {
            return;
        }
        lessonIds.stream().filter(id -> id != null && id > 0).distinct().sorted(Comparator.naturalOrder())
                .forEach(lessonId -> {
                    Query query = Query.query(Criteria.where("_id").is(lessonFenceId(lessonId)));
                    Update update = new Update()
                            .inc("fence", 1L)
                            .set("updated_at", now)
                            .setOnInsert("student_id", 0L)
                            .setOnInsert("lesson_id", lessonId)
                            .setOnInsert("group_id", groupId);
                    mongoTemplate.findAndModify(
                            query,
                            update,
                            FindAndModifyOptions.options().upsert(true).returnNew(true),
                            CheckinPairStateDocument.class);
                });
    }

    public boolean isTransferredSource(long lessonId) {
        return mongoTemplate.exists(
                Query.query(Criteria.where("_id").is(Long.toString(lessonId))),
                LessonTransferFenceDocument.class);
    }

    public static String lessonFenceId(long lessonId) {
        return "lesson:" + lessonId;
    }

    public static String pairId(long studentId, long lessonId) {
        return studentId + ":" + lessonId;
    }
}
