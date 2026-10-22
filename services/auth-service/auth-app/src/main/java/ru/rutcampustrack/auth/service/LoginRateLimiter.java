package ru.rutcampustrack.auth.service;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.session.AuthSessionException;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * IMP-02 + M03a Группа 11 (01 P0-6): progressive login rate limiting с composite key {@code (ip,
 * login)}.
 *
 * <p>Thresholds:
 *
 * <ul>
 *   <li>5 failures in 60 min → block 5 minutes
 *   <li>10 failures in 60 min → block 30 minutes
 *   <li>20 failures in 60 min → block 2 hours
 * </ul>
 *
 * <p><b>Why composite key.</b> Раньше ключ был просто {@code <login>} — злоумышленник с botnet мог
 * блокировать аккаунт жертвы заранее (DoS-by-rate-limit). Теперь счётчик ведётся per {@code (ip,
 * login)}: атакующий с одного IP лочит только СВОЮ попытку брута, пользователь с другого IP
 * спокойно логинится. Distributed brute через множество IP останавливается ещё раньше —
 * Gateway-level rate-limit (5 req/min per IP) на {@code POST /api/auth/login} (см. {@code
 * docs/api/api-rate-limits.md}).
 *
 * <p>Redis keys auto-expire (TTL = block window), unblock автоматический.
 */
@Service
public class LoginRateLimiter {

    private static final String ATTEMPTS_KEY_PREFIX = "login_attempts:";
    private static final String BLOCK_KEY_PREFIX = "login_blocked:";

    /**
     * M03b Группа 9 (KI-6): atomic INCR + EXPIRE через Lua. Убирает TTL-race, когда network blip
     * между INCR и EXPIRE = persistent key без expiry. Возвращает новое значение счётчика (count
     * после INCR).
     */
    private static final String INCREMENT_SCRIPT =
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrementScript;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrementScript = new DefaultRedisScript<>(INCREMENT_SCRIPT, Long.class);
    }

    /** Check if {@code (ip, login)} pair is currently blocked. Throws if blocked. */
    public void checkBlocked(String ip, String login) {
        String blockKey = blockKey(ip, login);
        Boolean blocked = redisCall(() -> redisTemplate.hasKey(blockKey));
        if (blocked == null) throw unavailable();
        if (blocked) {
            Long ttl = redisCall(() -> redisTemplate.getExpire(blockKey));
            long minutes = ttl != null && ttl > 0 ? (ttl + 59) / 60 : 1;
            throw new OtpRateLimitException(
                    "Account temporarily locked. Try again in " + minutes + " minutes");
        }
    }

    /**
     * Record a failed login attempt for {@code (ip, login)} pair. Atomic INCR+EXPIRE через
     * Lua-script — без TTL-race при network blip.
     */
    public void recordFailure(String ip, String login) {
        String attemptsKey = attemptsKey(ip, login);
        String blockKey = blockKey(ip, login);
        Long count =
                redisCall(
                        () ->
                                redisTemplate.execute(
                                        incrementScript,
                                        List.of(attemptsKey),
                                        String.valueOf(Duration.ofMinutes(60).toSeconds())));
        if (count == null) throw unavailable();

        // Progressive blocking
        if (count >= 20) {
            block(blockKey, Duration.ofHours(2));
            redisCall(() -> redisTemplate.delete(attemptsKey));
        } else if (count >= 10) {
            block(blockKey, Duration.ofMinutes(30));
        } else if (count >= 5) {
            block(blockKey, Duration.ofMinutes(5));
        }
    }

    /** Clear failure counter on successful login for {@code (ip, login)} pair. */
    public void clearFailures(String ip, String login) {
        redisCall(() -> redisTemplate.delete(attemptsKey(ip, login)));
        redisCall(() -> redisTemplate.delete(blockKey(ip, login)));
    }

    private void block(String key, Duration ttl) {
        redisCall(
                () -> {
                    redisTemplate.opsForValue().set(key, "1", ttl);
                    return null;
                });
    }

    private <T> T redisCall(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    private static AuthSessionException unavailable() {
        return new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
    }

    private static String attemptsKey(String ip, String login) {
        return ATTEMPTS_KEY_PREFIX + safeIp(ip) + ":" + login;
    }

    private static String blockKey(String ip, String login) {
        return BLOCK_KEY_PREFIX + safeIp(ip) + ":" + login;
    }

    private static String safeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}
