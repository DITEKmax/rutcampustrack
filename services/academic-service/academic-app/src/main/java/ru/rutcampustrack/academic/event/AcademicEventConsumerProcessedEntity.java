package ru.rutcampustrack.academic.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.rutcampustrack.shared.outbox.jpa.IdempotencyEntity;

/**
 * JPA-entity для таблицы {@code event_consumer_processed} (Flyway V18, M13 G8).
 *
 * <p>Подтип {@link IdempotencyEntity} — все поля и поведение в MappedSuperclass.
 * {@code JpaIdempotencyStore} принимает {@code AcademicEventConsumerProcessedEntity.class}
 * в конструкторе.
 */
@Entity
@Table(name = "event_consumer_processed")
public class AcademicEventConsumerProcessedEntity extends IdempotencyEntity {
}
