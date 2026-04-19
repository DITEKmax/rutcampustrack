package ru.rutcampustrack.shared.outbox;

/**
 * Status жизненного цикла outbox-записи.
 *
 * <ul>
 *   <li>{@link #PENDING} — записан listener'ом, ещё не отправлен в Rabbit.
 *       OutboxPublisherJob подхватывает эти rows при каждом тике.</li>
 *   <li>{@link #SENT} — успешно отправлен. Удаляется OutboxCleanupJob через 7 дней (NEW-7).</li>
 *   <li>{@link #FAILED} — отправка не удалась после всех retry attempts.
 *       retry_count / last_error заполнены. Требует manual intervention.</li>
 * </ul>
 *
 * <p>В БД хранится lowercase (`pending` / `sent` / `failed`) — CLAUDE.md правило
 * про enum-ы (lowercase строки в PG).
 */
public enum OutboxStatus {
    PENDING,
    SENT,
    FAILED
}
