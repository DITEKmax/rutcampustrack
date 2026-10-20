package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * M16 G7 — unit-тесты для {@link RedisHeadmanRateLimiter}.
 *
 * <p>Mock'аем {@link StringRedisTemplate} вместо Testcontainers — проверка логики INCR + EXPIRE +
 * fail-open без stretching CI runtime.
 */
class RedisHeadmanRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisHeadmanRateLimiter limiter;
    private MeterRegistry meterRegistry;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        meterRegistry = new SimpleMeterRegistry();
        limiter =
                new RedisHeadmanRateLimiter(
                        redis, new HeadmanRateLimitProperties(300, 60), meterRegistry);
    }

    @Test
    @DisplayName("INCR=300 (на лимите) → tryConsume=true (300-й проходит)")
    void atLimitAllowed() {
        when(valueOps.increment(anyString())).thenReturn(300L);
        assertThat(limiter.tryConsume(42L)).isTrue();
    }

    @Test
    @DisplayName("INCR=301 (over) → tryConsume=false")
    void overLimitRejected() {
        when(valueOps.increment(anyString())).thenReturn(301L);
        assertThat(limiter.tryConsume(42L)).isFalse();
    }

    @Test
    @DisplayName("Redis exception → fail-open (true) + counter инкремент")
    void redisFailureFailOpen() {
        when(valueOps.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis down"));

        boolean allowed = limiter.tryConsume(42L);

        assertThat(allowed).isTrue();
        assertThat(meterRegistry.counter("headman_rl_redis_failures_total").count()).isEqualTo(1.0);
    }
}
