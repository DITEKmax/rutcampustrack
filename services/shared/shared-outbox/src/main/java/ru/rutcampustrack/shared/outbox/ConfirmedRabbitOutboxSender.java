package ru.rutcampustrack.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.ReturnListener;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import ru.rutcampustrack.shared.events.EventTraceContext;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** One confirmed mandatory publication on a dedicated channel; no global returns callback. */
public final class ConfirmedRabbitOutboxSender implements OutboxEventSender {
    private static final ObjectMapper METADATA = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ENVELOPE = new TypeReference<>() {};
    private final RabbitTemplate template;

    public ConfirmedRabbitOutboxSender(RabbitTemplate template) {
        this.template = template;
        if (!(template.getConnectionFactory()
                instanceof CachingConnectionFactory connectionFactory)) {
            throw new IllegalArgumentException(
                    "Confirmed outbox publication requires a bounded caching connection factory");
        }
        // Configure the existing factory before listeners/publishers start. TLS, SASL, recovery
        // and transaction settings are preserved; the default publisher factory shares the same
        // native client, and checkout timeout is propagated by CachingConnectionFactory.
        connectionFactory.setConnectionTimeout(3000);
        connectionFactory.getRabbitConnectionFactory().setHandshakeTimeout(3000);
        connectionFactory.getRabbitConnectionFactory().setChannelRpcTimeout(3000);
        connectionFactory.setChannelCheckoutTimeout(3000);
        connectionFactory.setCloseTimeout(3000);
        template.setMandatory(true);
        template.setObservationEnabled(true);
    }

    @Override
    public void send(String eventType, String payload) {
        Map<String, Object> envelope;
        String eventId;
        try {
            envelope = METADATA.readValue(payload, ENVELOPE);
            if (!(envelope.get("event_id") instanceof String value)) {
                throw new IllegalArgumentException("Missing event identity");
            }
            eventId = UUID.fromString(value).toString();
        } catch (JsonProcessingException
                | IllegalArgumentException
                | NullPointerException failure) {
            throw new OutboxDeliveryException(OutboxDeliveryException.Reason.INVALID_EVENT);
        }
        Message message =
                MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setContentEncoding("UTF-8")
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(eventId)
                        .setTimestamp(Date.from(Instant.now()))
                        .setHeader("event_type", eventType)
                        .build();
        Object requestId = envelope.get("request_id");
        if (requestId instanceof String value && canonicalUuid(value)) {
            message.getMessageProperties().setHeader("request_id", value);
        }
        try (var ignored = EventTraceContext.openParent(envelope)) {
            sendConfirmed("rut-uit.events", "", message);
        }
    }

    /** Delayed attendance recovery retains the original body/IDs and the current worker span. */
    public void sendToQueue(String queue, Message message) {
        sendConfirmed("", queue, message);
    }

    private void sendConfirmed(String exchange, String routingKey, Message message) {
        template.invoke(
                operations ->
                        operations.execute(
                                channel -> {
                                    AtomicBoolean returned = new AtomicBoolean();
                                    ReturnListener listener =
                                            (code, text, returnedExchange, key, properties, body) ->
                                                    returned.set(true);
                                    channel.addReturnListener(listener);
                                    try {
                                        // execute() inside invoke() uses the same dedicated
                                        // channel. RabbitMQ emits
                                        // basic.return before basic.ack, so confirm alone
                                        // cannot hide NO_ROUTE.
                                        operations.send(exchange, routingKey, message);
                                        if (!operations.waitForConfirms(5000)) {
                                            throw new OutboxDeliveryException(
                                                    OutboxDeliveryException.Reason.BROKER_NACK);
                                        }
                                        if (returned.get()) {
                                            throw new OutboxDeliveryException(
                                                    OutboxDeliveryException.Reason
                                                            .BROKER_UNROUTABLE);
                                        }
                                        return null;
                                    } finally {
                                        channel.removeReturnListener(listener);
                                    }
                                }));
    }

    private static boolean canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
