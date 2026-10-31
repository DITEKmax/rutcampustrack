package ru.rutcampustrack.auth.service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView.Origin;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;

/**
 * Shared effects only. ADMIN/HEADMAN callers separately authorize and lock identity before
 * sessions.
 */
@Component
public class GeneratedPasswordMutation {
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordChangedOutbox outbox;
    private final SecureRandom random = new SecureRandom();

    public GeneratedPasswordMutation(
            JdbcTemplate jdbc, PasswordEncoder encoder, PasswordChangedOutbox outbox) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.outbox = outbox;
    }

    public boolean generate(long userId, long expectedVersion, Origin origin) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || (origin != Origin.HEADMAN_RESET && origin != Origin.ADMIN_RESET))
            throw new IllegalStateException("authorized generated-credential transaction required");
        StringBuilder generated = new StringBuilder(12);
        for (int i = 0; i < 12; i++)
            generated.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        String password = generated.toString();
        // Auth's tuple trigger advances credentials_version exactly once and fences bot cards.
        int changed =
                jdbc.update(
                        """
                UPDATE auth_identities SET password_hash=?,initial_password=?,password_changed=FALSE,
                    generated_password_origin=? WHERE id=? AND credentials_version=?
                """,
                        encoder.encode(password),
                        password,
                        origin.code(),
                        userId,
                        expectedVersion);
        if (changed != 1) return false;
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE password_reset_tokens SET used_at=? WHERE user_id=? AND used_at IS NULL",
                Timestamp.from(now),
                userId);
        jdbc.update(
                """
                UPDATE auth_sessions SET revoked_at=?,revoke_reason='PASSWORD_CHANGED'
                WHERE user_id=? AND revoked_at IS NULL
                """,
                Timestamp.from(now),
                userId);
        jdbc.update(
                "INSERT INTO account_security_events(user_id,sid,event_type,occurred_at) VALUES (?,NULL,'PASSWORD_CHANGED',?)",
                userId,
                Timestamp.from(now));
        outbox.append(userId, now);
        return true;
    }
}
