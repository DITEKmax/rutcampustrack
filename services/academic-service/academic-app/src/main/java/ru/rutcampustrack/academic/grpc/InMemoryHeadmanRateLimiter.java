package ru.rutcampustrack.academic.grpc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M16 G7 — in-memory fallback {@link HeadmanRateLimiter} для случаев когда
 * Redis выключен (например, юнит-тесты без Testcontainers Redis).
 *
 * <p>Это ровно тот же ConcurrentHashMap + sliding-window TokenBucket код,
 * что был в {@link AcademicGrpcServiceImpl} до M16 G7 (M06 G8b implementation),
 * вынесенный в отдельный bean. Активируется только когда
 * {@code academic.headman-rate-limit.redis-enabled=false}.
 *
 * <p><b>WARNING:</b> при horizontal scale-out (multiple academic pods) этот
 * limiter не shared — каждый pod видит свои buckets. Это и был bug в
 * future-ideas.md F05 что закрывает M16 G7 через {@link RedisHeadmanRateLimiter}.
 */
@Component
@ConditionalOnProperty(prefix = "academic.headman-rate-limit",
        name = "redis-enabled", havingValue = "false")
public class InMemoryHeadmanRateLimiter implements HeadmanRateLimiter {

    private static final int RL_MAX_BUCKETS = 10_000;

    private final HeadmanRateLimitProperties properties;
    private final ConcurrentHashMap<Long, TokenBucket> buckets = new ConcurrentHashMap<>();

    public InMemoryHeadmanRateLimiter(HeadmanRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean tryConsume(long userId) {
        if (buckets.size() > RL_MAX_BUCKETS) {
            buckets.clear();
        }
        TokenBucket bucket = buckets.computeIfAbsent(userId, id -> new TokenBucket(properties));
        return bucket.tryConsume();
    }

    /** Sliding-window token bucket. Lock-free через CAS (AtomicLong). */
    private static final class TokenBucket {
        private final AtomicLong tokens;
        private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
        private final HeadmanRateLimitProperties properties;
        private final long windowNanos;

        TokenBucket(HeadmanRateLimitProperties props) {
            this.properties = props;
            this.windowNanos = props.windowSeconds() * 1_000_000_000L;
            this.tokens = new AtomicLong(props.perMinute());
        }

        boolean tryConsume() {
            long now = System.nanoTime();
            long lastRefill = lastRefillNanos.get();
            long elapsed = now - lastRefill;
            if (elapsed >= windowNanos
                    && lastRefillNanos.compareAndSet(lastRefill, now)) {
                tokens.set(properties.perMinute());
            }

            while (true) {
                long current = tokens.get();
                if (current <= 0) {
                    return false;
                }
                if (tokens.compareAndSet(current, current - 1)) {
                    return true;
                }
            }
        }
    }
}
