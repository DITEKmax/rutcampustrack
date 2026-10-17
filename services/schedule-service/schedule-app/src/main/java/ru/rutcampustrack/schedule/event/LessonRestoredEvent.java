package ru.rutcampustrack.schedule.event;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import ru.rutcampustrack.shared.events.EventVersion;

/** Actual resulting physical lesson, after the existing assignment/date restore authority. */
@EventVersion(1)
public final class LessonRestoredEvent extends DomainEvent {
    public LessonRestoredEvent(
            Object source,
            long sourceLessonId,
            long actorId,
            Map<String, Object> restored,
            OffsetDateTime restoredAt) {
        super(source, "lesson.restored", payload(sourceLessonId, actorId, restored));
        setOccurredAt(restoredAt);
    }

    private static Map<String, Object> payload(
            long sourceLessonId, long actorId, Map<String, Object> restored) {
        Map<String, Object> payload =
                new LinkedHashMap<>(LessonNotificationSnapshot.from(restored));
        payload.put("source_lesson_id", sourceLessonId);
        payload.put("actor_user_id", actorId);
        return payload;
    }
}
