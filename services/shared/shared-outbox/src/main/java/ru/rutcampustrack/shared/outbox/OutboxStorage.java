package ru.rutcampustrack.shared.outbox;

import java.time.Instant;
import java.util.List;

/**
 * Storage-agnostic API для outbox-паттерна (M02, NEW-6).
 *
 * <p>Две реализации: {@code JpaOutboxStorage} (academic + schedule, PG) и
 * {@code MongoOutboxStorage} (attendance). {@code OutboxPublisherJob} и
 * {@code OutboxCleanupJob} работают через этот интерфейс — без привязки к
 * конкретному storage.
 *
 * <p>Контракт методов:
 * <ul>
 *   <li>{@link #save(String, String)} — append-only insert в {@code PENDING}.
 *       Должен вызываться ВНУТРИ той же {@code @Transactional} что и доменная
 *       операция (atomicity — событие либо записывается вместе с доменными
 *       данными, либо не записывается вовсе).</li>
 *   <li>{@link #findPending(int)} — batch read в FIFO-порядке (order by
 *       created_at). Не должен менять state — publisher сам помечает sent
 *       после успешной отправки.</li>
 *   <li>{@link #markSent(Object)} — атомарный переход PENDING→SENT.</li>
 *   <li>{@link #markFailed(Object, String)} — атомарный переход в FAILED с
 *       инкрементом retry_count и записью last_error.</li>
 *   <li>{@link #deleteSentBefore(Instant)} — cleanup для retention (NEW-7).</li>
 *   <li>{@link #countPending()} — gauge для Micrometer метрики
 *       {@code outbox.lag}.</li>
 * </ul>
 *
 * <p>Storage-специфичные детали (JPA EntityManager, Mongo template) — внутри
 * конкретных реализаций. API ничего не знает про транзакции — caller
 * оборачивает save() в свою tx.
 */
public interface OutboxStorage {

    /**
     * Сохраняет новую outbox-запись в статусе PENDING.
     *
     * @param eventType тип события ({@code "lesson.started"} и т.п.)
     * @param payload   сериализованный JSON payload (non-null, non-empty)
     */
    void save(String eventType, String payload);

    /**
     * Читает batch pending-записей в FIFO-порядке (по created_at ASC).
     *
     * @param limit максимальное число записей (≥1)
     * @return список pending-записей (возможно пустой)
     */
    List<OutboxRecord> findPending(int limit);

    /**
     * Атомарно помечает запись SENT с указанием {@code sent_at = now}.
     *
     * @param id идентификатор записи (тип соответствует конкретному storage)
     */
    void markSent(Object id);

    /**
     * Атомарно помечает запись FAILED, инкрементирует retry_count, записывает
     * last_error (truncated до 2000 chars).
     *
     * @param id       идентификатор записи
     * @param errorMsg сообщение об ошибке (может быть null)
     */
    void markFailed(Object id, String errorMsg);

    /**
     * Удаляет записи в статусе SENT старше указанного момента (retention
     * policy, NEW-7 — 7 дней).
     *
     * @param before cutoff timestamp — всё ДО него удаляется
     * @return количество удалённых записей
     */
    long deleteSentBefore(Instant before);

    /**
     * Возвращает число записей в статусе PENDING. Используется Micrometer
     * метрикой {@code outbox.lag}.
     */
    long countPending();
}
