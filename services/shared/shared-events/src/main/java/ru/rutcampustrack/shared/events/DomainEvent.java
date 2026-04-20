package ru.rutcampustrack.shared.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Базовый класс для всех domain events (NEW-60, P2-1/5, QA3, M04 D5(a)).
 *
 * <p>Envelope-поля — snake_case в JSON (соответствует event-schemas):
 * <ul>
 *   <li>{@code event_type} — строковый идентификатор события (например
 *       {@code "lesson.started"}). Соответствует filename в event-schemas/.</li>
 *   <li>{@code event_id} — UUID конкретного экземпляра события.</li>
 *   <li>{@code event_version} — версия schema из {@link EventVersion}
 *       (auto-fill в {@link AbstractEventPublisher}).</li>
 *   <li>{@code trace_id} — correlation id (auto-fill из MDC в
 *       {@link AbstractEventPublisher}).</li>
 *   <li>{@code occurred_at} — время события.</li>
 *   <li>{@code source} — сервис-источник (например {@code "academic-service"}).</li>
 *   <li>{@code payload} — domain-специфичные данные (POJO/record, сериализуется Jackson'ом).</li>
 * </ul>
 *
 * <p>Extends Spring's {@code ApplicationEvent} — сервисы публикуют через
 * {@code applicationEventPublisher.publishEvent(...)}, а
 * {@code @TransactionalEventListener} в каждом сервисе сериализует payload
 * и пишет в outbox table.
 *
 * <p>{@code @JsonIgnoreProperties} прячет служебные поля Spring
 * ApplicationEvent ({@code source}, {@code timestamp}). Наше поле {@code source}
 * (сервис-источник) объявлено отдельно с {@code @JsonProperty}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"timestamp"})
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public abstract class DomainEvent extends ApplicationEvent {

    @JsonProperty("event_type")
    private final String eventType;

    @JsonProperty("event_id")
    private UUID eventId;

    @JsonProperty("event_version")
    private Integer eventVersion;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("occurred_at")
    private OffsetDateTime occurredAt;

    @JsonProperty("source")
    private String sourceService;

    @JsonProperty("payload")
    private final Object payload;

    /**
     * @param eventType строковый идентификатор события (snake_case dot-separated, например {@code "lesson.started"}).
     * @param payload   domain-данные (POJO/record), сериализуется Jackson'ом.
     */
    protected DomainEvent(String eventType, Object payload) {
        super(eventType);
        this.eventType = eventType;
        this.payload = payload;
        this.eventId = UUID.randomUUID();
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Integer getEventVersion() {
        return eventVersion;
    }

    public void setEventVersion(Integer eventVersion) {
        this.eventVersion = eventVersion;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    @JsonProperty("source")
    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }

    public Object getPayload() {
        return payload;
    }
}
