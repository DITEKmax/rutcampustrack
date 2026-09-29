package ru.rutcampustrack.schedule.event;

import ru.rutcampustrack.shared.events.EventVersion;

/** Immutable, bounded transfer command persisted through Schedule's SQL outbox. */
@EventVersion(1)
public final class LessonTransferRequestedEvent extends DomainEvent {

    public LessonTransferRequestedEvent(Object source, Object payload) {
        super(source, "lesson.transfer.requested", payload);
    }
}
