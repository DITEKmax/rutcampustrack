package ru.rutcampustrack.shared.outbox;

import org.springframework.amqp.AmqpException;

/** Safe finite transport failure codes; no broker/payload/exception text is retained. */
public final class OutboxDeliveryException extends AmqpException {
    public enum Reason {
        INVALID_EVENT,
        BROKER_UNROUTABLE,
        BROKER_NACK,
        TRANSPORT_UNAVAILABLE
    }

    private final Reason reason;

    public OutboxDeliveryException(Reason reason) {
        super("Outbox delivery failed: " + reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
