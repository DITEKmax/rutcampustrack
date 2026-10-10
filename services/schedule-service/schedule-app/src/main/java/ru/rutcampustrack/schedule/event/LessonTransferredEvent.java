package ru.rutcampustrack.schedule.event;

import ru.rutcampustrack.shared.events.EventVersion;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Confirmed transfer, emitted in the transaction that completes its participant protocol. */
@EventVersion(1)
public final class LessonTransferredEvent extends DomainEvent {
    public LessonTransferredEvent(Object source, UUID operationId, long actorId,
                                  Map<String, Object> before, Map<String, Object> after,
                                  OffsetDateTime completedAt) {
        super(source, "lesson.transferred", Map.of("operation_id", operationId.toString(),
                "actor_user_id", actorId, "source", LessonNotificationSnapshot.from(before),
                "target", LessonNotificationSnapshot.from(after)));
        setOccurredAt(completedAt);
    }
}
