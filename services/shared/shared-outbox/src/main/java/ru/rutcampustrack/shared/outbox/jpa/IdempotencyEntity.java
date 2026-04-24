package ru.rutcampustrack.shared.outbox.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Абстрактный JPA {@link MappedSuperclass} для consumer-side dedup
 * таблицы {@code event_consumer_processed} (M13 G8).
 *
 * <p>Конкретные сервисы (academic, schedule) объявляют подкласс с
 * {@code @Entity} + {@code @Table(name = "event_consumer_processed")}.
 * Схема таблицы — Flyway миграции V18 (academic) / V14 (schedule).
 *
 * <p>Composite PK ({@code consumer_id}, {@code event_id}) — идентифицирует
 * уникальную пару «consumer обработал этот event». UNIQUE constraint
 * на эту пару даёт атомарный insert-or-fail в {@code JpaIdempotencyStore.tryClaim}.
 */
@MappedSuperclass
@IdClass(IdempotencyEntity.PK.class)
public abstract class IdempotencyEntity {

    @Id
    @Column(name = "consumer_id", nullable = false, length = 64)
    private String consumerId;

    @Id
    @Column(name = "event_id", nullable = false, columnDefinition = "uuid")
    private UUID eventId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected IdempotencyEntity() {
    }

    @PrePersist
    void onCreate() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    /**
     * Composite PK class — требование JPA для {@code @IdClass}. Должен
     * иметь public no-arg конструктор + equals/hashCode.
     */
    public static class PK implements Serializable {
        private String consumerId;
        private UUID eventId;

        public PK() {
        }

        public PK(String consumerId, UUID eventId) {
            this.consumerId = consumerId;
            this.eventId = eventId;
        }

        public String getConsumerId() {
            return consumerId;
        }

        public UUID getEventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(consumerId, pk.consumerId)
                    && Objects.equals(eventId, pk.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumerId, eventId);
        }
    }
}
