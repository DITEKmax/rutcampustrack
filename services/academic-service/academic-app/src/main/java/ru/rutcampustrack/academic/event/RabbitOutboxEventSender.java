package ru.rutcampustrack.academic.event;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;

import java.nio.charset.StandardCharsets;

/**
 * Транспортный shim: читает сериализованный JSON из outbox и шлёт в
 * RabbitMQ fanout exchange {@code rut-uit.events}. Pub через
 * {@link RabbitTemplate}, content-type выставляется вручную (payload уже JSON,
 * re-serialize не нужен).
 */
@Component
public class RabbitOutboxEventSender implements OutboxEventSender {

    private static final String EXCHANGE = "rut-uit.events";

    private final RabbitTemplate rabbitTemplate;

    public RabbitOutboxEventSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(String eventType, String payload) {
        rabbitTemplate.send(EXCHANGE, "", MessageBuilder
                .withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("UTF-8")
                .setHeader("event_type", eventType)
                .build());
    }
}
