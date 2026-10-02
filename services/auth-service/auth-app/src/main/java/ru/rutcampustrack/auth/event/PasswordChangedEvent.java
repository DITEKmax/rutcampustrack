package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Metadata persisted atomically with a password change/reset, then delivered by outbox. */
public final class PasswordChangedEvent extends DomainEvent {

    public record Payload(@JsonProperty("telegram_id") long telegramId) {
        public Payload {
            if (telegramId <= 0) {
                throw new IllegalArgumentException("telegramId must be positive");
            }
        }
    }

    public PasswordChangedEvent(Object source, long telegramId) {
        super(source, "password.changed", new Payload(telegramId));
    }
}
