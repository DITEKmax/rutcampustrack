package ru.rutcampustrack.schedule.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.rutcampustrack.shared.outbox.jpa.IdempotencyEntity;

/**
 * JPA-entity для таблицы {@code event_consumer_processed} (Flyway V14, M13 G8).
 * Подтип {@link IdempotencyEntity} — все поля и поведение в MappedSuperclass.
 */
@Entity
@Table(name = "event_consumer_processed")
public class ScheduleEventConsumerProcessedEntity extends IdempotencyEntity {
}
