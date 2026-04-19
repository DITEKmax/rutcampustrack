package ru.rutcampustrack.schedule.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * Записывает Spring ApplicationEvent'ы в schedule_outbox (M02 Группа 5).
 *
 * <p>Изменение относительно v1: раньше был
 * {@code @TransactionalEventListener(AFTER_COMMIT)} → прямой
 * {@code rabbitTemplate.convertAndSend(...)}. Проблема — потеря сообщений
 * при Rabbit outage после коммита (02 P0-6).
 *
 * <p>Новая схема: {@link TransactionPhase#BEFORE_COMMIT} — listener
 * срабатывает ВНУТРИ активной tx. Запись в outbox идёт в той же
 * транзакции, что и доменная операция. {@code OutboxPublisherJob}
 * асинхронно публикует pending rows в Rabbit.
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public DomainEventListener(OutboxStorage outboxStorage, ObjectMapper objectMapper) {
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(DomainEvent event) {
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
