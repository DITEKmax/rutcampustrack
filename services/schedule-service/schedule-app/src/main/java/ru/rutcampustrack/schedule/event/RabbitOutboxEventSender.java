package ru.rutcampustrack.schedule.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.shared.outbox.ConfirmedRabbitOutboxSender;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;

/** Retains the original outbox intent unless a mandatory publication is broker-confirmed. */
@Component
public class RabbitOutboxEventSender implements OutboxEventSender {
    private final ConfirmedRabbitOutboxSender sender;

    public RabbitOutboxEventSender(RabbitTemplate rabbitTemplate) {
        this.sender = new ConfirmedRabbitOutboxSender(rabbitTemplate);
    }

    @Override
    public void send(String eventType, String payload) {
        sender.send(eventType, payload);
    }
}
