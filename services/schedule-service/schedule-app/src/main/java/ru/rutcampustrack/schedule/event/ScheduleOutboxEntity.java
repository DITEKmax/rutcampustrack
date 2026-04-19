package ru.rutcampustrack.schedule.event;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import ru.rutcampustrack.shared.outbox.jpa.OutboxEntity;

/**
 * JPA-entity для таблицы {@code schedule_outbox} (Flyway V11, M02 Группа 3).
 * Подтип {@link OutboxEntity} — все поля и поведение в MappedSuperclass.
 */
@Entity
@Table(name = "schedule_outbox")
public class ScheduleOutboxEntity extends OutboxEntity {
}
