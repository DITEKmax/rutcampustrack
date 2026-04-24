package ru.rutcampustrack.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Storage-agnostic API для consumer-side dedup по {@code event_id}
 * (M02 CRITICAL #2, M13 G8).
 *
 * <p>Две реализации в shared-outbox: {@code JpaIdempotencyStore}
 * (academic + schedule, PostgreSQL) и {@code MongoIdempotencyStore}
 * (attendance + notification-web, MongoDB).
 *
 * <p>Контракт: {@link #tryClaim(String, UUID)} — атомарный insert
 * {@code (consumer_id, event_id)} с UNIQUE constraint. Возвращает
 * {@code true} если запись новая (consumer должен обработать), либо
 * {@code false} если duplicate detected (skip).
 *
 * <p>Должен вызываться **внутри той же {@code @Transactional}** что и
 * handler — при rollback handler'а должен откатиться и claim, чтобы
 * RabbitMQ redelivery смог retry.
 *
 * <p>Retention: cleanup-job удаляет записи старше 7 дней (RabbitMQ
 * не хранит события дольше).
 */
public interface IdempotencyStore {

    /**
     * Пытается атомарно зарегистрировать факт обработки события.
     *
     * @param consumerId логический id consumer'а ({@code "academic"},
     *                   {@code "schedule"}, {@code "attendance"},
     *                   {@code "notification-web"}, {@code "notification-history"})
     * @param eventId    UUID события (из envelope.event_id)
     * @return {@code true} — claim успешен, событие можно обработать;
     *         {@code false} — уже обработано ранее, skip
     */
    boolean tryClaim(String consumerId, UUID eventId);

    /**
     * Удаляет записи старше {@code before}. Cleanup-job вызывает раз в
     * сутки. Retention 7 дней — события за пределами TTL точно не
     * прилетят повторно.
     *
     * @param before cutoff timestamp — всё ДО него удаляется
     * @return количество удалённых записей
     */
    long deleteProcessedBefore(Instant before);
}
