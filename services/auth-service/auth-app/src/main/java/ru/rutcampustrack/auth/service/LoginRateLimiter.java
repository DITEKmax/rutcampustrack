package ru.rutcampustrack.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

import java.time.Duration;
import java.util.List;

/**
 * IMP-02 + M03a Группа 11 (01 P0-6): progressive login rate limiting
 * с composite key {@code (ip, login)}.
 *
 * <p>Thresholds:
 * <ul>
 *   <li>5 failures in 60 min → block 5 minutes</li>
 *   <li>10 failures in 60 min → block 30 minutes</li>
 *   <li>20 failures in 60 min → block 2 hours</li>
 * </ul>
 *
 * <p><b>Why composite key.</b> Раньше ключ был просто {@code <login>} —
 * злоумышленник с botnet мог блокировать аккаунт жертвы заранее (DoS-by-rate-limit).
 * Теперь счётчик ведётся per {@code (ip, login)}: атакующий с одного IP лочит только
 * СВОЮ попытку брута, пользователь с другого IP спокойно логинится. Distributed brute
 * через множество IP останавливается ещё раньше — Gateway-level rate-limit (5 req/min
 * per IP) на {@code POST /api/auth/login} (см. {@code docs/api/api-rate-limits.md}).</p>
 *
 * <p>Redis keys auto-expire (TTL = block window), unblock автоматический.</p>
 */
@Service
public class LoginRateLimiter {

    private static final String ATTEMPTS_KEY_PREFIX = "login_attempts:";
    private static final String BLOCK_KEY_PREFIX = "login_blocked:";

    /**
     * M03b Группа 9 (KI-6): atomic INCR + EXPIRE через Lua. Убирает TTL-race,
     * когда network blip между INCR и EXPIRE = persistent key без expiry.
     * Возвращает новое значение счётчика (count после INCR).
     */
    private static final String INCREMENT_SCRIPT = """
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

    /**
     * Check if {@code (ip, login)} pair is currently blocked. Throws if blocked.
     */
    public void checkBlocked(String ip, String login) {
        String blockKey = blockKey(ip, login);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(blockKey))) {
            Long ttl = redisTemplate.getExpire(blockKey);
            long minutes = ttl != null && ttl > 0 ? (ttl + 59) / 60 : 1;
            throw new OtpRateLimitException(
                    "Account temporarily locked. Try again in " + minutes + " minutes");
        }
    }

    /**
     * Record a failed login attempt for {@code (ip, login)} pair.
     * Atomic INCR+EXPIRE через Lua-script — без TTL-race при network blip.
     */
    public void recordFailure(String ip, String login) {
        String attemptsKey = attemptsKey(ip, login);
        String blockKey = blockKey(ip, login);
        Long count = redisTemplate.execute(incrementScript,
                List.of(attemptsKey),
                String.valueOf(Duration.ofMinutes(60).toSeconds()));
        if (count == null) return;

        // Progressive blocking
        if (count >= 20) {
            redisTemplate.opsForValue().set(blockKey, "1", Duration.ofHours(2));
            redisTemplate.delete(attemptsKey);
        } else if (count >= 10) {
            redisTemplate.opsForValue().set(blockKey, "1", Duration.ofMinutes(30));
        } else if (count >= 5) {
            redisTemplate.opsForValue().set(blockKey, "1", Duration.ofMinutes(5));
        }
    }

    /**
     * Clear failure counter on successful login for {@code (ip, login)} pair.
     */
    public void clearFailures(String ip, String login) {
        redisTemplate.delete(attemptsKey(ip, login));
        redisTemplate.delete(blockKey(ip, login));
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
