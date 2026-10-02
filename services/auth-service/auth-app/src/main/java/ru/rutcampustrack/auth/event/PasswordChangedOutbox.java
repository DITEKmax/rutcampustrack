package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

import java.time.Instant;
import java.time.ZoneOffset;

/** Records only notification metadata in the existing credential transaction. */
@Component
public final class PasswordChangedOutbox extends AbstractEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PasswordChangedOutbox(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        super("auth-service");
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void append(long userId, Instant occurredAt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("password notification requires a credential transaction");
        }
        // Caller already holds this user's row lock. Read the recipient before commit,
        // so a post-commit lookup failure cannot lose the notification.
        Long telegramId = jdbc.queryForObject("SELECT telegram_id FROM users WHERE id = ?",
                Long.class, userId);
        if (telegramId == null || telegramId <= 0) {
            return;
        }
        PasswordChangedEvent event = new PasswordChangedEvent(this, telegramId);
        event.setOccurredAt(occurredAt.atOffset(ZoneOffset.UTC));
        fillDefaults(event);
        try {
            jdbc.update("INSERT INTO auth_outbox (event_type, payload) VALUES (?, CAST(? AS jsonb))",
                    event.getEventType(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize password notification metadata", exception);
        }
    }
}
