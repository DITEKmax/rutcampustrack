package ru.rutcampustrack.attendance.event;

import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ConflictException;

import java.time.Instant;

/** Mongo transaction fence acquired before lesson and student/lesson locks. */
@Component
public class SemesterArchiveFence {

    private final MongoTemplate mongoTemplate;

    public SemesterArchiveFence(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public SemesterArchiveFenceDocument lock(long semesterId, Instant now) {
        if (semesterId <= 0) {
            throw new IllegalArgumentException("semesterId must be positive");
        }
        Query query = Query.query(Criteria.where("_id").is(Long.toString(semesterId)));
        Update update = new Update()
                .inc("write_fence", 1L)
                .set("updated_at", now)
                .setOnInsert("semester_id", semesterId)
                .setOnInsert("barrier_state", "OPEN");
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                SemesterArchiveFenceDocument.class);
    }

    public SemesterArchiveFenceDocument lockWritable(long semesterId, Instant now) {
        SemesterArchiveFenceDocument fence = lock(semesterId, now);
        if (!isWritable(fence)) {
            throw new ConflictException("Изменения посещаемости недоступны: семестр архивируется или архивирован");
        }
        return fence;
    }

    /**
     * Schedule effects already committed to its outbox may drain while the
     * participant is PREPAREd. The SEAL command closes this narrow admission.
     */
    public SemesterArchiveFenceDocument lockAcceptedScheduleEffect(long semesterId, Instant now) {
        SemesterArchiveFenceDocument fence = lock(semesterId, now);
        String state = fence == null ? null : fence.getBarrierState();
        if (!"OPEN".equals(state) && !"RELEASED".equals(state)
                && !"ARCHIVE_PREPARING".equals(state)) {
            throw new SemesterArchiveEffectRejectedException("SEMESTER_ARCHIVE_SEALED",
                    "Schedule effect arrived after the semester archive seal");
        }
        return fence;
    }

    public boolean isWritable(SemesterArchiveFenceDocument fence) {
        return fence != null && ("OPEN".equals(fence.getBarrierState())
                || "RELEASED".equals(fence.getBarrierState()));
    }

    public void transition(long semesterId, long stateVersion, String operationId,
                           String barrierState, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(Long.toString(semesterId)));
        Update update = new Update()
                .set("semester_id", semesterId)
                .set("state_version", stateVersion)
                .set("operation_id", operationId)
                .set("barrier_state", barrierState)
                .set("updated_at", now);
        if (mongoTemplate.updateFirst(query, update, SemesterArchiveFenceDocument.class).getMatchedCount() != 1) {
            throw new IllegalStateException("Semester archive fence disappeared during command handling");
        }
    }
}
