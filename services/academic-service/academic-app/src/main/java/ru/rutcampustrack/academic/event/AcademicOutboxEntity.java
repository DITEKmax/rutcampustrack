package ru.rutcampustrack.academic.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.rutcampustrack.shared.outbox.jpa.OutboxEntity;

/**
 * JPA-entity для таблицы {@code academic_outbox} (Flyway V16, M02 Группа 3).
 *
 * <p>Конкретный подтип {@link OutboxEntity} — все поля и поведение в
 * MappedSuperclass, здесь только маппинг на имя таблицы. {@code JpaOutboxStorage}
 * принимает {@code AcademicOutboxEntity.class} в конструкторе.
 */
@Entity
@Table(name = "academic_outbox")
public class AcademicOutboxEntity extends OutboxEntity {
}
