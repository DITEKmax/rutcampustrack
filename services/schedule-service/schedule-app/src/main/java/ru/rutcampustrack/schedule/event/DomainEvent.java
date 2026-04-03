package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Abstract base class for all domain events published by Schedule Service.
 * Provides the event envelope: event_type, event_id (UUID), occurred_at.
 * Each subclass defines a nested Payload record with entity-specific fields.
 * <p>
 * Per D-03: serialized JSON is {"event_type": "...", "event_id": "...", "occurred_at": "...", "payload": {...}}
 * Per D-04: services publish Spring ApplicationEvent subclasses; DomainEventListener forwards to RabbitMQ.
 */
@JsonIgnoreProperties({"source", "timestamp"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class DomainEvent extends ApplicationEvent {

    @JsonProperty("event_type")
    private final String eventType;

    @JsonProperty("event_id")
    private final UUID eventId;

    @JsonProperty("occurred_at")
    private final OffsetDateTime occurredAt;

    @JsonProperty("payload")
    private final Object payload;

    protected DomainEvent(Object source, String eventType, Object payload) {
        super(source);
        this.eventType = eventType;
        this.eventId = UUID.randomUUID();
        this.occurredAt = OffsetDateTime.now();
        this.payload = payload;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Object getPayload() {
        return payload;
    }
}
