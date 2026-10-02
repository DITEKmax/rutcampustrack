package ru.rutcampustrack.schedule.event;

import ru.rutcampustrack.shared.events.EventVersion;

/** V2 uses the same transfer protocol for an exact one-off origin instead of a template. */
@EventVersion(2)
public final class OneOffLessonTransferRequestedEvent extends DomainEvent {
    public OneOffLessonTransferRequestedEvent(Object source, Object payload) {
        super(source, "lesson.transfer.requested", payload);
    }
}
