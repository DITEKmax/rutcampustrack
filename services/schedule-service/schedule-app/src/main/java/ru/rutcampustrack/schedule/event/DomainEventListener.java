package ru.rutcampustrack.schedule.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Bridges Spring domain events to RabbitMQ after the database transaction commits.
 * <p>
 * Per D-04: receives DomainEvent subclasses published by service layer via ApplicationEventPublisher,
 *           then forwards them to the RabbitMQ fanout exchange.
 * Per D-05: services are decoupled from AMQP — this class is the only one that knows RabbitTemplate.
 * Per research decision (STATE.md): @TransactionalEventListener(AFTER_COMMIT) guarantees
 *   no message is published if the database transaction rolls back.
 */
@Component
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);
    private static final String EXCHANGE = "rut-uit.events";

    private final RabbitTemplate rabbitTemplate;

    public DomainEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(DomainEvent event) {
        log.debug("Publishing event: type={}, id={}", event.getEventType(), event.getEventId());
        rabbitTemplate.convertAndSend(EXCHANGE, "", event);
    }
}
