package ru.rutcampustrack.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

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
            throw new IllegalStateException(
                    "password notification requires a credential transaction");
        }
        // Caller already holds this user's row lock. Read the recipient before commit,
        // so a post-commit lookup failure cannot lose the notification.
        List<CredentialMetadata> rows =
                jdbc.query(
                        """
                SELECT telegram_id, credentials_version, telegram_binding_version,
                       EXISTS(SELECT 1 FROM bot_credential_messages m WHERE m.user_id = users.id
                           AND m.cleanup_state = 'PENDING') AS cleanup_pending
                FROM users WHERE id = ? FOR UPDATE
                """,
                        (row, index) -> {
                            long telegram = row.getLong("telegram_id");
                            Long recipient = row.wasNull() ? null : telegram;
                            return new CredentialMetadata(
                                    recipient,
                                    row.getLong("credentials_version"),
                                    row.getLong("telegram_binding_version"),
                                    row.getBoolean("cleanup_pending"));
                        },
                        userId);
        if (rows.size() != 1) throw new IllegalStateException("password event owner unavailable");
        var metadata = rows.getFirst();
        if (metadata.telegramId == null && !metadata.cleanupPending) {
            return;
        }
        PasswordChangedEvent event =
                new PasswordChangedEvent(
                        this,
                        userId,
                        metadata.credentialsVersion,
                        metadata.bindingVersion,
                        metadata.telegramId);
        event.setOccurredAt(occurredAt.atOffset(ZoneOffset.UTC));
        fillDefaults(event);
        try {
            jdbc.update(
                    "INSERT INTO auth_outbox (event_type, payload) VALUES (?, CAST(? AS jsonb))",
                    event.getEventType(),
                    objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "cannot serialize password notification metadata", exception);
        }
    }

    private record CredentialMetadata(
            Long telegramId,
            long credentialsVersion,
            long bindingVersion,
            boolean cleanupPending) {}
}
