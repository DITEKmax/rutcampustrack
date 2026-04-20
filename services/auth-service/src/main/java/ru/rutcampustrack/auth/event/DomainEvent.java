package ru.rutcampustrack.auth.event;

/**
 * Auth-service alias для {@link ru.rutcampustrack.shared.events.DomainEvent}
 * (M04 D5(a) — единый envelope across services).
 */
public abstract class DomainEvent extends ru.rutcampustrack.shared.events.DomainEvent {

    protected DomainEvent(Object source, String eventType, Object payload) {
        super(eventType, payload);
    }
}
