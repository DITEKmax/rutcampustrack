package ru.rutcampustrack.attendance.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed deduplication and rate limiting for geo-checkin.
 * Per D-17, D-18, D-19:
 * - acquireDedup: 5-second lock to prevent duplicate submissions
 * - checkRateLimit: max 3 attempts per 60 seconds per user
 *
 * CRITICAL: Boolean.TRUE.equals(set) — setIfAbsent returns null on connection failure.
 * CRITICAL: count == 1L check for EXPIRE — set TTL only on first increment to avoid race.
 */
@Service
public class CheckinRateLimiter {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 3;

    private final StringRedisTemplate redis;

    public CheckinRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Acquires a deduplication lock for a specific student+lesson combination.
     * Returns true if the lock was acquired (first call within 5s), false otherwise.
     *
     * @param lessonId the lesson ID
     * @param userId   the student user ID
     * @return true if dedup lock acquired, false if already locked or error
     */
    public boolean acquireDedup(Long lessonId, Long userId) {
        String key = "attendance:dedup:" + lessonId + ":" + userId;
        Boolean set = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(5));
        return Boolean.TRUE.equals(set);
    }

    /**
     * Checks and enforces rate limit for a user (max 3 checkin attempts per minute).
     * Returns true if under limit, false if limit exceeded.
     *
     * @param userId the student user ID
     * @return true if within rate limit, false if exceeded
     */
    public boolean checkRateLimit(Long userId) {
        String key = "attendance:rate:" + userId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(60));
        }
        return count != null && count <= MAX_ATTEMPTS_PER_MINUTE;
    }
}
