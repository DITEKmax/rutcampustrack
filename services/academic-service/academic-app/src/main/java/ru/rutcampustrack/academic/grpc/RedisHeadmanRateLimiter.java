package ru.rutcampustrack.academic.grpc;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * M16 G7 — Redis-backed rate-limiter для gRPC isHeadman вызовов.
 *
 * <p>Pattern: {@code INCR rl:headman:{userId}:{minute}} + {@code EXPIRE
 * windowSeconds + 5} только при первом INCR. {@code minute} = floor(epoch / 60),
 * sliding-aligned. Атомарно и без Lua-скриптов.
 *
 * <p>Activated by default (см. {@link #PROPERTY}). Fallback на
 * {@link InMemoryHeadmanRateLimiter} автоматический если Redis выключен
 * через {@code academic.headman-rate-limit.redis-enabled=false} (например в
 * тестах без Redis).
 *
 * <p>Fail-open at Redis outage (см. DECISIONS § D11): если RedisException —
 * пропускаем call, инкрементим counter {@code headman_rl_redis_failures_total}
 * для visibility. Это **не security-critical** path (защита от flood, не от
 * brute-force), потеря rate-limit окна на время Redis-outage acceptable.
 */
@Component
@ConditionalOnProperty(prefix = "academic.headman-rate-limit",
        name = "redis-enabled", havingValue = "true", matchIfMissing = true)
public class RedisHeadmanRateLimiter implements HeadmanRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisHeadmanRateLimiter.class);

    /** Property для override default'а в тестах: {@code academic.headman-rate-limit.redis-enabled}. */
    public static final String PROPERTY = "academic.headman-rate-limit.redis-enabled";

    private static final String KEY_PREFIX = "rl:headman:";

    private final StringRedisTemplate redis;
    private final HeadmanRateLimitProperties properties;
    private final Counter redisFailureCounter;

    public RedisHeadmanRateLimiter(StringRedisTemplate redis,
                                   HeadmanRateLimitProperties properties,
                                   MeterRegistry meterRegistry) {
        this.redis = Objects.requireNonNull(redis);
        this.properties = Objects.requireNonNull(properties);
        this.redisFailureCounter = Counter.builder("headman_rl_redis_failures_total")
                .description("Redis failures during headman rate-limit check (fail-open path)")
                .register(meterRegistry);
    }

    @Override
    public boolean tryConsume(long userId) {
        long minuteBucket = Instant.now().getEpochSecond() / 60;
        String key = KEY_PREFIX + userId + ":" + minuteBucket;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // Первый call в этом окне — ставим TTL чуть больше окна
                // для cleanup (clock skew tolerance).
                redis.expire(key, Duration.ofSeconds(properties.windowSeconds() + 5L));
            }
            return count == null || count <= properties.perMinute();
        } catch (Exception e) {
            // Fail-open: Redis недоступен → пропускаем call, инкрементим counter.
            // Headman RL — flood protection, не security-critical (см. D11).
            redisFailureCounter.increment();
            log.warn("Redis failure in headman RL (fail-open): userId={} err={}",
                    userId, e.toString());
            return true;
        }
    }
}
