package ru.rutcampustrack.attendance.groupdeletion;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.stereotype.Component;

import java.util.*;

/** One stable transactional group document serializes every group business writer with deletion. */
@Component
public class GroupDeletionMongoFence {
    public static final String FENCES = "group_deletion_fences";
    public static final String SCOPES = "group_deletion_lesson_scopes";
    public static final Set<String> COLLECTIONS =
            Set.of(
                    "attendances",
                    "late_checkin_requests",
                    "excuse_tickets",
                    "request_attachments",
                    "lesson_actions",
                    "student_checkin_pairs",
                    "student_checkin_receipts",
                    "student_request_receipts",
                    "lesson_cancellation_markers");
    private final MongoTemplate mongo;

    public GroupDeletionMongoFence(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void write(long group) {
        var row = touch(group);
        if (row.getString("operation") != null)
            throw new ru.rutcampustrack.attendance.exception.ConflictException(
                    "Группа закрыта для окончательного удаления");
    }

    /** Resolve lesson-only writers locally; unresolved scope must retry, never write unfenced. */
    public long lessonGroup(long lesson, Long semester, Long expectedGroup) {
        Document mapping = mongo.findById(Long.toString(lesson), Document.class, SCOPES);
        if (mapping == null)
            mapping =
                    mongo.findById(Long.toString(lesson), Document.class, "group_deletion_lessons");
        Set<Long> groups = new TreeSet<>();
        if (mapping != null) {
            if (semester != null && ((Number) mapping.get("semester_id")).longValue() != semester)
                throw scopeConflict();
            groups.add(((Number) mapping.get("group_id")).longValue());
        }
        if (expectedGroup != null) {
            if (!groups.isEmpty() && !groups.contains(expectedGroup)) throw scopeConflict();
            return expectedGroup;
        }
        for (String collection : COLLECTIONS) {
            for (Document row :
                    mongo.find(
                            Query.query(
                                    Criteria.where("lesson_id")
                                            .is(lesson)
                                            .and("group_id")
                                            .ne(null)),
                            Document.class,
                            collection)) {
                if (row.get("group_id") instanceof Number group && group.longValue() > 0)
                    groups.add(group.longValue());
            }
        }
        if (groups.size() != 1) throw scopeConflict();
        return groups.iterator().next();
    }

    private static RuntimeException scopeConflict() {
        return new ru.rutcampustrack.attendance.exception.ConflictException(
                "Не удалось однозначно определить группу урока; повторите действие");
    }

    public boolean deletedLesson(long lesson, long semester, Long group) {
        Document row =
                mongo.findById(Long.toString(lesson), Document.class, "group_deletion_lessons");
        if (row == null) {
            Document fence =
                    group == null
                            ? null
                            : mongo.findById(Long.toString(group), Document.class, FENCES);
            return fence != null && Boolean.TRUE.equals(fence.getBoolean("deleted"));
        }
        if (!(row.get("semester_id") instanceof Number value)
                || value.longValue() != semester
                || group != null
                        && (!(row.get("group_id") instanceof Number owner)
                                || owner.longValue() != group))
            throw new ru.rutcampustrack.attendance.exception.ConflictException(
                    "Удалённая пара принадлежит другому scope");
        return true;
    }

    public Document touch(long group) {
        if (group <= 0) throw new IllegalArgumentException("groupId must be positive");
        return mongo.findAndModify(
                Query.query(Criteria.where("_id").is(Long.toString(group))),
                new Update().inc("revision", 1L),
                FindAndModifyOptions.options().upsert(true).returnNew(true),
                Document.class,
                FENCES);
    }
}
