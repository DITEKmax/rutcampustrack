package ru.rutcampustrack.shared.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Базовый класс для всех domain events (NEW-60, P2-1/5, QA3).
 *
 * <p>Поля — snake_case в JSON (соответствует event-schemas):
 * <ul>
 *   <li>{@code event_version} — версия schema из {@link EventVersion}.</li>
 *   <li>{@code trace_id} — correlation id (auto-fill из MDC в
 *       {@link AbstractEventPublisher}).</li>
 *   <li>{@code occurred_at} — время события (UTC).</li>
 *   <li>{@code source} — сервис-источник (например {@code "academic-service"}).</li>
 * </ul>
 *
 * <p>Подклассы могут быть record'ами или классами — DomainEvent хранит
 * общие поля и делегирует сериализацию Jackson'у. Jackson умеет
 * сериализовать поля naming через {@code @JsonProperty} — подклассы
 * объявляют собственные поля как обычно.
 *
 * <p>Protected конструктор — подклассы передают данные явно (поля final
 * после build'а). Auto-fill по null'ам делает {@link AbstractEventPublisher}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class DomainEvent {

    @JsonProperty("event_version")
    private Integer eventVersion;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("occurred_at")
    private Instant occurredAt;

    @JsonProperty("source")
    private String source;

    protected DomainEvent() {
    }

    protected DomainEvent(Integer eventVersion, String traceId, Instant occurredAt, String source) {
        this.eventVersion = eventVersion;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
        this.source = source;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
