package ru.rutcampustrack.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.event.EventListener;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

/**
 * Слушает Spring {@link DomainEvent} и отправляет в RabbitMQ fanout.
 *
 * <p>Auth Service не работает в JPA-транзакции при публикации OTP-событий,
 * поэтому используем обычный {@code @EventListener} (синхронно после вызова
 * {@code ApplicationEventPublisher.publishEvent()}), а не TransactionalEventListener.
 *
 * <p>Ошибки AMQP логируются, но не пробрасываются: сбой rabbit'а не должен
 * ронять основной поток аутентификации.
 *
 * <p>M09 G2.6: бин регистрируется {@code @Bean}'ом в {@link RabbitConfig}
 * (не через {@code @Component}), чтобы получить именно наш
 * {@code RabbitTemplate} с {@code Jackson2JsonMessageConverter}, а не
 * default-autoconfig'овый с {@code SimpleMessageConverter} (байты/Java).
 */
public class DomainEventListener extends AbstractEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventListener.class);

    private final RabbitTemplate rabbitTemplate;

    public DomainEventListener(RabbitTemplate rabbitTemplate) {
        super("auth-service");
        this.rabbitTemplate = rabbitTemplate;
    }

    @EventListener
    public void onDomainEvent(DomainEvent event) {
        // M04 QA3 — auto-fill envelope перед сериализацией Jackson'ом в AMQP message converter.
        fillDefaults(event);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, "", event);
            log.debug("Published event: type={}, id={}", event.getEventType(), event.getEventId());
        } catch (Exception e) {
            // M09 G2.6: catch Exception (не только AmqpException) — сериализация
            // Jackson'ом бросает MessageConversionException/IllegalStateException,
            // которые раньше пробрасывались и ломали /auth/otp/request. Сбой
            // rabbit'а/сериализации не должен ронять основной поток.
            log.warn("Failed to publish event {} ({}): {}",
                    event.getEventType(), event.getEventId(), e.getMessage());
        }
    }
}
