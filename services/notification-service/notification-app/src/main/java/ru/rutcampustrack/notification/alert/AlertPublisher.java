package ru.rutcampustrack.notification.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * M04 Группа 9 — публикует события {@code alert.fired} в fanout-обмен
 * {@code rut-uit.events}. notification-bot consumer поймает их
 * (EventDispatcher.handlers map) и отправит админу в Telegram.
 *
 * <p>Envelope совместим с shared-events (M04 G6): поля
 * {@code event_type}, {@code event_id}, {@code event_version=1},
 * {@code occurred_at}, {@code source="notification-web"},
 * {@code trace_id} (random UUID — alert init не имеет parent span).
 * Payload — данные alert'а: name, severity, summary, description,
 * labels, status (firing|resolved).
 */
@Component
public class AlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(AlertPublisher.class);
    private static final String EXCHANGE = "rut-uit.events";

    private final RabbitTemplate rabbitTemplate;

    public AlertPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishFired(AlertPayload payload) {
        Map<String, Object> envelope = Map.of(
                "event_type", "alert.fired",
                "event_id", UUID.randomUUID().toString(),
                "event_version", 1,
                "trace_id", UUID.randomUUID().toString(),
                "source", "notification-web",
                "occurred_at", Instant.now().toString(),
                "payload", payload.toMap()
        );
        rabbitTemplate.convertAndSend(EXCHANGE, "", envelope);
        log.info("Published alert.fired: name={} severity={} status={}",
                payload.name(), payload.severity(), payload.status());
    }
}
