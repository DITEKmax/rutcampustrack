package ru.rutcampustrack.shared.events;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker-аннотация для consumer-методов, которые применяют consumer-side
 * dedup через {@link IdempotencyGuard} (M13 G8).
 *
 * <p>Аннотация **не активирует AOP** — реальный dedup делается явным
 * вызовом {@code idempotencyGuard.tryClaim(consumer, envelope)} в
 * первой строке handler'а. Цель аннотации — статическая документация и
 * grep-able marker для ArchUnit-проверок «все @RabbitListener методы
 * должны иметь @EventIdempotent».
 *
 * <p>Пример:
 * <pre>{@code
 * @RabbitListener(queues = "academic-service.events")
 * @EventIdempotent(consumer = "academic")
 * @Transactional
 * public void onEvent(Map<String, Object> envelope) {
 *     if (!idempotencyGuard.tryClaim("academic", envelope)) return;
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventIdempotent {

    /**
     * Логический id consumer'а — должен совпадать со строкой, переданной
     * в {@link IdempotencyGuard#tryClaim(String, java.util.Map)}.
     * Используется как partition-key в таблице {@code event_consumer_processed}.
     */
    String consumer();
}
