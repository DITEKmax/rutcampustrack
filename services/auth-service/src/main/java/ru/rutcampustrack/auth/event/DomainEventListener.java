package ru.rutcampustrack.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Слушает Spring {@link DomainEvent} и отправляет в RabbitMQ fanout.
 *
 * <p>Auth Service не работает в JPA-транзакции при публикации OTP-событий,
 * поэтому используем обычный {@code @EventListener} (синхронно после вызова
 * {@code ApplicationEventPublisher.publishEvent()}), а не TransactionalEventListener.
 *
 * <p>Ошибки AMQP логируются, но не пробрасываются: сбой rabbit'а не должен
 * ронять основной поток аутентификации.
 */
@Component
@ConditionalOnBean(RabbitTemplate.class)
public class DomainEventListener {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final RabbitTemplate rabbitTemplate;

    public DomainEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "", event);
            log.debug("Published event: type={}, id={}", event.getEventType(), event.getEventId());
        } catch (AmqpException e) {
            log.warn("Failed to publish event {} ({}): {}",
                    event.getEventType(), event.getEventId(), e.getMessage());
        }
    }
}
