package ru.rutcampustrack.shared.outbox;

import java.time.Instant;

/**
 * DTO-view для одной outbox-записи, которую {@code OutboxPublisherJob}
 * забирает у storage. Storage-agnostic — не зависит от JPA/Mongo.
 *
 * @param id        уникальный идентификатор (Long для JPA, String для Mongo).
 *                  Хранится как {@link Object} чтобы storage сам выбирал тип.
 * @param eventType имя события ({@code "lesson.started"}, {@code "attendance.marked"}).
 *                  Используется как routing hint и для метрик `outbox.published.total`.
 * @param payload   уже сериализованный JSON body события (string).
 *                  Не парсится на стороне publisher'а — passes через.
 * @param createdAt время записи listener'ом. Для метрик / troubleshooting.
 */
public record OutboxRecord(
        Object id,
        String eventType,
        String payload,
        Instant createdAt
) {
}
