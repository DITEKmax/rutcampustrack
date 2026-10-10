package ru.rutcampustrack.academic.event;

import java.util.Map;

/** Empty signal: consumers reload the committed dashboard through its REST APIs. */
public final class DashboardInvalidatedEvent extends DomainEvent {
    public DashboardInvalidatedEvent(Object source) {
        super(source, "dashboard.invalidated", Map.of());
    }
}
