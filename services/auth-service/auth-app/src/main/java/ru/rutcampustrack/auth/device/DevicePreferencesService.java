package ru.rutcampustrack.auth.device;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.auth.dto.DevicePreferencesResponse;
import ru.rutcampustrack.auth.dto.UpdateDevicePreferencesRequest;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.session.AuthSessionException;

/** Lock order is identity -> session (admission) -> device, all held through commit. */
@Service
public class DevicePreferencesService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AuthService auth;
    private final SecureRandom random = new SecureRandom();
    private final long ttlSeconds;

    public DevicePreferencesService(
            JdbcTemplate jdbc,
            PlatformTransactionManager manager,
            AuthService auth,
            @Value("${auth.device-preferences.ttl-seconds:31536000}") long ttlSeconds) {
        if (ttlSeconds < 60 || ttlSeconds > 63072000)
            throw new IllegalArgumentException("Invalid device preference TTL");
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.auth = auth;
        this.ttlSeconds = ttlSeconds;
    }

    public Context read(SessionPrincipal principal, String selector) {
        return tx(
                () -> {
                    auth.admit(principal);
                    Row row = lock(selector);
                    Instant now = Instant.now();
                    String current = selector;
                    if (row == null || !row.expiry().isAfter(now)) {
                        byte[] bytes = new byte[32];
                        random.nextBytes(bytes);
                        current = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                        jdbc.update(
                                "INSERT INTO auth_device_preferences(selector_hash,expires_at) VALUES (?,?)",
                                hash(current),
                                Timestamp.from(now.plusSeconds(ttlSeconds)));
                        row = new Row(null, 0, now.plusSeconds(ttlSeconds));
                    } else {
                        jdbc.update(
                                "UPDATE auth_device_preferences SET expires_at=? WHERE selector_hash=?",
                                Timestamp.from(now.plusSeconds(ttlSeconds)),
                                hash(current));
                    }
                    return new Context(
                            new DevicePreferencesResponse(
                                    row.theme(), Long.toString(row.revision())),
                            current);
                });
    }

    public DevicePreferencesResponse write(
            SessionPrincipal principal, String selector, UpdateDevicePreferencesRequest request) {
        long expected;
        try {
            expected = Long.parseLong(request.expectedRevision());
        } catch (NumberFormatException e) {
            throw new DevicePreferencesException(DevicePreferencesException.Code.INVALID_REQUEST);
        }
        return tx(
                () -> {
                    auth.admit(principal);
                    Row row = lock(selector);
                    if (row == null || !row.expiry().isAfter(Instant.now()))
                        throw new DevicePreferencesException(
                                DevicePreferencesException.Code.DEVICE_CONTEXT_LOST);
                    if (row.revision() != expected || row.revision() == Long.MAX_VALUE)
                        throw new DevicePreferencesException(
                                DevicePreferencesException.Code.DEVICE_REVISION_CONFLICT);
                    jdbc.update(
                            "UPDATE auth_device_preferences SET theme=?,revision=revision+1,expires_at=? WHERE selector_hash=?",
                            request.theme(),
                            Timestamp.from(Instant.now().plusSeconds(ttlSeconds)),
                            hash(selector));
                    return new DevicePreferencesResponse(
                            request.theme(), Long.toString(row.revision() + 1));
                });
    }

    public int cleanup(int batch) {
        return tx(
                () ->
                        jdbc.update(
                                """
                DELETE FROM auth_device_preferences WHERE selector_hash IN
                (SELECT selector_hash FROM auth_device_preferences WHERE expires_at<?
                 ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED)
                """,
                                Timestamp.from(Instant.now()),
                                batch));
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    private Row lock(String selector) {
        if (selector == null || !selector.matches("[A-Za-z0-9_-]{43}")) return null;
        // Only canonical unpadded encodings are accepted.
        byte[] decoded = Base64.getUrlDecoder().decode(selector);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(selector))
            return null;
        var rows =
                jdbc.query(
                        "SELECT theme,revision,expires_at FROM auth_device_preferences WHERE selector_hash=? FOR UPDATE",
                        (rs, n) ->
                                new Row(
                                        rs.getString(1),
                                        rs.getLong(2),
                                        rs.getTimestamp(3).toInstant()),
                        hash(selector));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static byte[] hash(String selector) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(selector.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T tx(Supplier<T> action) {
        try {
            return transaction.execute(status -> action.get());
        } catch (DevicePreferencesException | AuthSessionException e) {
            throw e;
        } catch (RuntimeException e) {
            // Neither JDBC exception text nor the selector is exposed or logged.
            throw new DevicePreferencesException(
                    DevicePreferencesException.Code.DEVICE_PREFERENCES_UNAVAILABLE);
        }
    }

    public record Context(DevicePreferencesResponse response, String selector) {
        @Override
        public String toString() {
            return "DevicePreferencesContext[redacted]";
        }
    }

    private record Row(String theme, long revision, Instant expiry) {}
}
