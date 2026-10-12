package ru.rutcampustrack.auth.event;

import java.nio.charset.StandardCharsets;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;

/** A pending intent becomes sent only after the broker confirms acceptance. */
@Component
public class RabbitOutboxEventSender implements OutboxEventSender {
    private final RabbitTemplate rabbitTemplate;

    public RabbitOutboxEventSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void send(String eventType, String payload) {
        rabbitTemplate.invoke(
                operations -> {
                    operations.send(
                            RabbitConfig.EXCHANGE,
                            "",
                            MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                                    .setContentEncoding("UTF-8")
                                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                                    .setHeader("event_type", eventType)
                                    .build());
                    // OrDie also waits for channel.close, which can stall under a broker alarm.
                    if (!operations.waitForConfirms(5000)) {
                        throw new AmqpException("Broker did not confirm outbox event acceptance");
                    }
                    return null;
                });
    }
}
