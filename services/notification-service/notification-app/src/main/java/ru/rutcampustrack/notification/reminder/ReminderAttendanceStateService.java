package ru.rutcampustrack.notification.reminder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ReminderAttendanceStateService {

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public ReminderAttendanceStateService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public void recordMarked(Map<String, Object> payload) {
        Long lessonId = longValue(payload.get("lesson_id"));
        Long userId = longValue(payload.get("user_id"));
        if (lessonId == null || userId == null) {
            return;
        }

        Query query = byLessonAndUser(lessonId, userId);
        Update update = new Update()
                .set("lesson_id", lessonId)
                .set("user_id", userId)
                .set("status", stringValue(payload.get("status")))
                .set("marked_at", Instant.now(clock));
        mongoTemplate.upsert(query, update, ReminderAttendanceStateDocument.class);
    }

    /** One strict read owns the reminder eligibility snapshot; failures must reach listener retry. */
    public Set<Long> getUnmarkedUserIds(long lessonId, Set<Long> currentUserIds) {
        if (lessonId <= 0 || currentUserIds == null
                || currentUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)) {
            throw new IllegalArgumentException("Reminder lesson and recipient IDs must be positive integers");
        }
        if (currentUserIds.isEmpty()) {
            return Set.of();
        }
        Query query = new Query(Criteria.where("lesson_id").is(lessonId)
                .and("user_id").in(currentUserIds)).maxTime(Duration.ofSeconds(3));
        Set<Long> unmarkedUserIds = new HashSet<>(currentUserIds);
        mongoTemplate.find(query, ReminderAttendanceStateDocument.class)
                .forEach(document -> unmarkedUserIds.remove(document.getUserId()));
        return Set.copyOf(unmarkedUserIds);
    }

    public void deleteLessonState(Map<String, Object> payload) {
        Long lessonId = longValue(payload.get("lesson_id"));
        if (lessonId == null) {
            return;
        }
        mongoTemplate.remove(new Query(Criteria.where("lesson_id").is(lessonId)),
                ReminderAttendanceStateDocument.class);
    }

    private static Query byLessonAndUser(long lessonId, long userId) {
        return new Query(Criteria.where("lesson_id").is(lessonId).and("user_id").is(userId));
    }

    private static Long longValue(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String stringValue(Object value) {
        return value instanceof String s ? s : null;
    }
}
