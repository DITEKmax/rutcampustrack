package ru.rutcampustrack.attendance.student;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

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

    public static String pairId(long studentId, long lessonId) {
        return studentId + ":" + lessonId;
    }
}
