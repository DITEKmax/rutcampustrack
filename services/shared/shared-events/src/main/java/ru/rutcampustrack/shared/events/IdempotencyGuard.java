package ru.rutcampustrack.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;

/**
 * Helper для consumer-side dedup по {@code event_id} (M13 G8).
 *
 * <p>Использование в {@code @RabbitListener}-метода:
 * <pre>{@code
 * @RabbitListener(queues = "academic-service.events")
 * @Transactional
 * public void onEvent(Map<String, Object> envelope) {
 *     if (!idempotencyGuard.tryClaim("academic", envelope)) {
 *         return; // duplicate, уже обработано
 *     }
 *     // ... основной handler ...
 * }
 * }</pre>
 *
 * <p>Поведение при отсутствии {@code event_id} в envelope (M13 G24-fix-6):
 * helper пробрасывает {@link IllegalStateException} с ERROR-логом
 * (fail-closed). После M13 G8 все наши publisher'ы заполняют event_id
 * через {@link AbstractEventPublisher#fillDefaults} — событие без id =
 * либо bug в новом publisher'е, либо forged message. Раньше (до G24-fix-6)
 * fail-open возвращал true → security-auditor отметил это как H3:
 * compromise broker creds + drop event_id = bypass replay-защиты. Теперь
 * @Transactional handler rollback → DLQ → manual triage.
 *
 * <p>Helper потокобезопасен — не хранит state, всё в {@link IdempotencyStore}.
 */
public class IdempotencyGuard {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyGuard.class);

    private final IdempotencyStore store;

    public IdempotencyGuard(IdempotencyStore store) {
        this.store = store;
    }

    /**
     * Пытается claim'ить событие из envelope. Возвращает {@code true}
     * если consumer должен обработать, {@code false} если duplicate.
     *
     * <p>Если в envelope нет валидного {@code event_id} — кидает
     * {@link IllegalStateException} (M13 G24-fix-6 fail-closed). Caller
     * через @Transactional handler делает rollback, message идёт в DLQ.
     *
     * @param consumerId логический id consumer'а
     * @param envelope   сырой envelope (Map из Jackson после @RabbitListener)
     * @throws IllegalStateException если event_id отсутствует или malformed
     */
    public boolean tryClaim(String consumerId, Map<String, Object> envelope) {
        UUID eventId = extractEventId(envelope);
        if (eventId == null) {
            String eventType = envelope == null ? null : (String) envelope.get("event_type");
            log.error("Idempotency: rejecting event без event_id (fail-closed) — "
                            + "consumer={}, event_type={}. После M13 G8 все publisher'ы "
                            + "обязаны заполнять event_id (AbstractEventPublisher.fillDefaults).",
                    consumerId, eventType);
            throw new IllegalStateException(
                    "Event без event_id rejected (fail-closed, M13 G24-fix-6): "
                            + "consumer=" + consumerId + ", event_type=" + eventType);
        }
        boolean claimed = store.tryClaim(consumerId, eventId);
        if (!claimed) {
            String eventType = (String) envelope.get("event_type");
            log.info("Idempotency: duplicate event skipped (consumer={}, event_type={}, event_id={})",
                    consumerId, eventType, eventId);
        }
        return claimed;
    }

    /**
     * Извлекает {@code event_id} из envelope. Поддерживает оба представления:
     * Jackson по умолчанию десериализует UUID в String, но если caller
     * подал готовый UUID — тоже принимаем.
     */
    static UUID extractEventId(Map<String, Object> envelope) {
        if (envelope == null) {
            return null;
        }
        Object raw = envelope.get("event_id");
        if (raw == null) {
            return null;
        }
        if (raw instanceof UUID uuid) {
            return uuid;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException ex) {
            log.warn("Idempotency: malformed event_id '{}', processing without dedup", raw);
            return null;
        }
    }
}
