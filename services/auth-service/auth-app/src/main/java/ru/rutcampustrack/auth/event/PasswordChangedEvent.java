package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import ru.rutcampustrack.shared.events.EventVersion;

/** Metadata persisted atomically with a password change/reset, then delivered by outbox. */
@EventVersion(2)
public final class PasswordChangedEvent extends DomainEvent {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payload(
            @JsonProperty("user_id") long userId,
            @JsonProperty("credentials_version") long credentialsVersion,
            @JsonProperty("telegram_binding_version") long telegramBindingVersion,
            @JsonProperty("telegram_id") Long telegramId) {
        public Payload {
            if (userId <= 0 || credentialsVersion <= 0 || telegramBindingVersion <= 0
                    || telegramId != null && telegramId <= 0) {
                throw new IllegalArgumentException("password event identity and generations must be positive");
            }
        }
    }

    public PasswordChangedEvent(Object source, long userId, long credentialsVersion,
                                long telegramBindingVersion, Long telegramId) {
        super(source, "password.changed", new Payload(userId, credentialsVersion, telegramBindingVersion, telegramId));
        setEventVersion(2);
    }
}
