package ru.rutcampustrack.attendance.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CheckinRateLimiter.
 * Mocks StringRedisTemplate and ValueOperations — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class CheckinRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CheckinRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new CheckinRateLimiter(redis);
    }

    @Test
    void acquireDedup_firstCall_returnsTrue() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        boolean result = rateLimiter.acquireDedup(1L, 100L);

        assertThat(result).isTrue();
        verify(valueOperations).setIfAbsent("attendance:dedup:1:100", "1", Duration.ofSeconds(5));
    }

    @Test
    void acquireDedup_secondCall_returnsFalse() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        boolean result = rateLimiter.acquireDedup(1L, 100L);

        assertThat(result).isFalse();
    }

    @Test
    void acquireDedup_redisReturnsNull_returnsFalse() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(null);

        boolean result = rateLimiter.acquireDedup(1L, 100L);

        assertThat(result).isFalse();
    }

    @Test
    void checkRateLimit_countOne_returnsTrueAndSetsExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        boolean result = rateLimiter.checkRateLimit(100L);

        assertThat(result).isTrue();
        verify(redis).expire("attendance:rate:100", Duration.ofSeconds(60));
    }

    @Test
    void checkRateLimit_countThree_returnsTrueAndDoesNotSetExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        boolean result = rateLimiter.checkRateLimit(100L);

        assertThat(result).isTrue();
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void checkRateLimit_countFour_returnsFalse() {
        when(valueOperations.increment(anyString())).thenReturn(4L);

        boolean result = rateLimiter.checkRateLimit(100L);

        assertThat(result).isFalse();
    }
}
