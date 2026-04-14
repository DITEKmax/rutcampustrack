package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Абстрактное событие доменного уровня, публикуемое Auth Service.
 *
 * <p>Формат совпадает с academic-service/DomainEvent: {@code {event_type, event_id, occurred_at, payload}}.
 * Подкласс определяет свой payload record. DomainEventListener пробрасывает в RabbitMQ fanout
 * {@code rut-uit.events} после коммита транзакции (если она есть) или сразу.
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
