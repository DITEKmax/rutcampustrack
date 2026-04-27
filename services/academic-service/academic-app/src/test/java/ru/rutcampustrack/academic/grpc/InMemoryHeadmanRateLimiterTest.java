package ru.rutcampustrack.academic.grpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M16 G7 — unit-тесты для in-memory fallback {@link InMemoryHeadmanRateLimiter}.
 *
 * <p>Проверяют что fallback работает идентично прежнему ConcurrentHashMap +
 * TokenBucket внутри AcademicGrpcServiceImpl (M06 G8b implementation).
 */
class InMemoryHeadmanRateLimiterTest {

    private static HeadmanRateLimitProperties props(int perMinute) {
        return new HeadmanRateLimitProperties(perMinute, 60);
    }

    @Test
    @DisplayName("первые N вызовов под лимитом → tryConsume=true")
    void allowsCallsUnderLimit() {
        InMemoryHeadmanRateLimiter limiter = new InMemoryHeadmanRateLimiter(props(5));
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(1L)).isTrue();
        }
    }

    @Test
    @DisplayName("(N+1)-й вызов → tryConsume=false (rate-limit hit)")
    void rejectsCallOverLimit() {
        InMemoryHeadmanRateLimiter limiter = new InMemoryHeadmanRateLimiter(props(5));
        for (int i = 0; i < 5; i++) {
            limiter.tryConsume(1L);
        }
        assertThat(limiter.tryConsume(1L)).isFalse();
    }

    @Test
    @DisplayName("разные userId — независимые bucket'ы")
    void independentBucketsPerUserId() {
        InMemoryHeadmanRateLimiter limiter = new InMemoryHeadmanRateLimiter(props(2));

        // user 1 исчерпал
        limiter.tryConsume(1L);
        limiter.tryConsume(1L);
        assertThat(limiter.tryConsume(1L)).isFalse();

        // user 2 ещё с полным quota'ой
        assertThat(limiter.tryConsume(2L)).isTrue();
        assertThat(limiter.tryConsume(2L)).isTrue();
        assertThat(limiter.tryConsume(2L)).isFalse();
    }

    @Test
    @DisplayName("limit 300 (M16 G7 default) — 300 проходят, 301 отбит")
    void defaultLimitIs300() {
        InMemoryHeadmanRateLimiter limiter = new InMemoryHeadmanRateLimiter(props(300));
        for (int i = 0; i < 300; i++) {
            assertThat(limiter.tryConsume(1L)).as("call %d", i).isTrue();
        }
        assertThat(limiter.tryConsume(1L)).isFalse();
    }
}
