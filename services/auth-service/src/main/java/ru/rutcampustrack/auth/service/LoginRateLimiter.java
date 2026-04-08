package ru.rutcampustrack.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

import java.time.Duration;

/**
 * IMP-02: Progressive login rate limiting per account.
 *
 * <p>Thresholds:
 * <ul>
 *   <li>5 failures in 5 min → block 5 minutes</li>
 *   <li>10 failures in 30 min → block 30 minutes</li>
 *   <li>20 failures in 60 min → block 2 hours</li>
 * </ul>
 *
 * <p>Redis keys auto-expire (TTL = block window), so unblock is automatic.
 */
@Service
public class LoginRateLimiter {

    private static final String ATTEMPTS_KEY_PREFIX = "login_attempts:";
    private static final String BLOCK_KEY_PREFIX = "login_blocked:";

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check if account is currently blocked. Throws if blocked.
     */
    public void checkBlocked(String login) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(BLOCK_KEY_PREFIX + login))) {
            Long ttl = redisTemplate.getExpire(BLOCK_KEY_PREFIX + login);
            long minutes = ttl != null && ttl > 0 ? (ttl + 59) / 60 : 1;
            throw new OtpRateLimitException(
                    "Account temporarily locked. Try again in " + minutes + " minutes");
        }
    }

    /**
     * Record a failed login attempt and apply progressive blocking.
     */
    public void recordFailure(String login) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + login;
        Long count = redisTemplate.opsForValue().increment(attemptsKey);
        if (count == null) return;

        // Set TTL on first attempt (60 min window for counting)
        if (count == 1L) {
            redisTemplate.expire(attemptsKey, Duration.ofMinutes(60));
        }

        // Progressive blocking
        if (count >= 20) {
            redisTemplate.opsForValue().set(BLOCK_KEY_PREFIX + login, "1", Duration.ofHours(2));
            redisTemplate.delete(attemptsKey);
        } else if (count >= 10) {
            redisTemplate.opsForValue().set(BLOCK_KEY_PREFIX + login, "1", Duration.ofMinutes(30));
        } else if (count >= 5) {
            redisTemplate.opsForValue().set(BLOCK_KEY_PREFIX + login, "1", Duration.ofMinutes(5));
        }
    }

    /**
     * Clear failure counter on successful login.
     */
    public void clearFailures(String login) {
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + login);
        redisTemplate.delete(BLOCK_KEY_PREFIX + login);
    }
}
