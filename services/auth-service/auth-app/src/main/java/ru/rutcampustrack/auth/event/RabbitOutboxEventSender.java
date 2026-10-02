package ru.rutcampustrack.auth.event;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;

import java.nio.charset.StandardCharsets;

/** A pending intent becomes sent only after the broker confirms acceptance. */
@Component
public class RabbitOutboxEventSender implements OutboxEventSender {
    private final RabbitTemplate rabbitTemplate;

    public RabbitOutboxEventSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(String eventType, String payload) {
        rabbitTemplate.invoke(operations -> {
            operations.send(RabbitConfig.EXCHANGE, "", MessageBuilder
                    .withBody(payload.getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("UTF-8")
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setHeader("event_type", eventType)
                    .build());
            operations.waitForConfirmsOrDie(5000);
            return null;
        });
    }
}
