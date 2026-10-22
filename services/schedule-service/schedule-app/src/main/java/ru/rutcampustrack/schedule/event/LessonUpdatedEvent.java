package ru.rutcampustrack.schedule.event;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import ru.rutcampustrack.shared.events.EventVersion;

/** Completed template room edit; it is not a change of lesson date or slot. */
@EventVersion(1)
public final class LessonUpdatedEvent extends DomainEvent {
    public LessonUpdatedEvent(
            Object source,
            UUID operationId,
            long actorId,
            Map<String, Object> before,
            Map<String, Object> after,
            OffsetDateTime completedAt) {
        super(
                source,
                "lesson.updated",
                Map.of(
                        "operation_id",
                        operationId.toString(),
                        "actor_user_id",
                        actorId,
                        "source",
                        LessonNotificationSnapshot.from(before),
                        "target",
                        LessonNotificationSnapshot.from(after)));
        setOccurredAt(completedAt);
    }
}
