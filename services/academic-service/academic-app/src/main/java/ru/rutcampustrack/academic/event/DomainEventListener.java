package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * Перехватывает Spring ApplicationEvent'ы от service-слоя и записывает их
 * в outbox-таблицу (M02 Группа 5, 02-Q3).
 *
 * <p>Изменение относительно v1: раньше был
 * {@code @TransactionalEventListener(AFTER_COMMIT)} → прямой
 * {@code rabbitTemplate.convertAndSend(...)}. Проблема — если БД-tx
 * закоммитилась, а Rabbit в этот момент недоступен, событие теряется
 * (02 P0-6 message loss).
 *
 * <p>Новая схема: {@link TransactionPhase#BEFORE_COMMIT} — listener
 * срабатывает ВНУТРИ активной tx, перед реальным commit'ом БД. Запись в
 * {@code academic_outbox} идёт в той же транзакции что и доменная
 * операция. Если outbox.save() бросает — tx откатывается (домен и outbox
 * либо оба, либо никак). Если tx откатывается по другой причине — запись
 * в outbox тоже откатывается.
 *
 * <p>Публикацию в Rabbit делает {@code OutboxPublisherJob} (shared-outbox)
 * асинхронно — при недоступности Rabbit row остаётся {@code pending},
 * следующий tick подхватывает.
 */
@Component
public class DomainEventListener extends AbstractEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public DomainEventListener(OutboxStorage outboxStorage, ObjectMapper objectMapper) {
        super("academic-service");
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        // M04 QA3 — auto-fill envelope (trace_id из MDC, event_version из @EventVersion,
        // occurred_at = now, source = "academic-service") перед сериализацией.
        fillDefaults(event);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize domain event: type=" + event.getEventType()
                            + ", id=" + event.getEventId(), e);
        }
        outboxStorage.save(event.getEventType(), payload);
        log.debug("Outbox saved: type={}, id={}", event.getEventType(), event.getEventId());
    }
}
