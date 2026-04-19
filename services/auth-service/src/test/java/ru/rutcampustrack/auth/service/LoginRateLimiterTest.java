package ru.rutcampustrack.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimiterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(redis);

    LoginRateLimiterTest() {
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("recordFailure: key формируется как login_attempts:<ip>:<login>")
    void recordFailure_compositeKey() {
        when(valueOps.increment("login_attempts:1.2.3.4:alice")).thenReturn(1L);

        limiter.recordFailure("1.2.3.4", "alice");

        verify(valueOps).increment("login_attempts:1.2.3.4:alice");
        verify(redis).expire("login_attempts:1.2.3.4:alice", Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("recordFailure: разные IP с одним login → разные ключи, НЕ аккумулируются")
    void recordFailure_differentIpsIsolated() {
        when(valueOps.increment("login_attempts:1.1.1.1:alice")).thenReturn(1L);
        when(valueOps.increment("login_attempts:2.2.2.2:alice")).thenReturn(1L);

        limiter.recordFailure("1.1.1.1", "alice");
        limiter.recordFailure("2.2.2.2", "alice");

        verify(valueOps).increment("login_attempts:1.1.1.1:alice");
        verify(valueOps).increment("login_attempts:2.2.2.2:alice");
        // Оба ключа установили TTL (разные counters, оба с count=1)
        verify(redis).expire("login_attempts:1.1.1.1:alice", Duration.ofMinutes(60));
        verify(redis).expire("login_attempts:2.2.2.2:alice", Duration.ofMinutes(60));
    }

    @Test
    @DisplayName("recordFailure: 5 попыток → блок на 5 минут")
    void recordFailure_5thAttempt_blocks5Min() {
        when(valueOps.increment("login_attempts:ip:login")).thenReturn(5L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("recordFailure: 10 попыток → блок на 30 минут")
    void recordFailure_10thAttempt_blocks30Min() {
        when(valueOps.increment(anyString())).thenReturn(10L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("recordFailure: 20 попыток → блок на 2 часа + delete счётчика")
    void recordFailure_20thAttempt_blocks2Hours() {
        when(valueOps.increment(anyString())).thenReturn(20L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofHours(2)));
        verify(redis).delete("login_attempts:ip:login");
    }

    @Test
    @DisplayName("recordFailure: 4 попыток → без блока")
    void recordFailure_belowThreshold_noBlock() {
        when(valueOps.increment(anyString())).thenReturn(4L);

        limiter.recordFailure("ip", "login");

        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("checkBlocked: заблокирован → OtpRateLimitException")
    void checkBlocked_blocked_throws() {
        when(redis.hasKey("login_blocked:1.1.1.1:alice")).thenReturn(true);
        when(redis.getExpire("login_blocked:1.1.1.1:alice")).thenReturn(300L);

        assertThatThrownBy(() -> limiter.checkBlocked("1.1.1.1", "alice"))
                .isInstanceOf(OtpRateLimitException.class)
                .hasMessageContaining("5 minutes");
    }

    @Test
    @DisplayName("checkBlocked: не заблокирован → проходит молча")
    void checkBlocked_notBlocked_passes() {
        when(redis.hasKey(anyString())).thenReturn(false);

        limiter.checkBlocked("1.1.1.1", "alice");

        verify(redis).hasKey("login_blocked:1.1.1.1:alice");
    }

    @Test
    @DisplayName("checkBlocked: IP jack на один login другим IP НЕ блокирует жертву")
    void checkBlocked_differentIpsIsolated() {
        // IP атакующего (2.2.2.2) заблокирован
        when(redis.hasKey("login_blocked:2.2.2.2:victim")).thenReturn(true);
        when(redis.getExpire("login_blocked:2.2.2.2:victim")).thenReturn(300L);
        // IP жертвы (1.1.1.1) НЕ заблокирован
        when(redis.hasKey("login_blocked:1.1.1.1:victim")).thenReturn(false);

        // Атакующий — блок
        assertThatThrownBy(() -> limiter.checkBlocked("2.2.2.2", "victim"))
                .isInstanceOf(OtpRateLimitException.class);
        // Жертва спокойно логинится
        limiter.checkBlocked("1.1.1.1", "victim");
    }

    @Test
    @DisplayName("clearFailures: удаляет оба ключа для (ip, login)")
    void clearFailures_deletesBothKeys() {
        limiter.clearFailures("1.1.1.1", "alice");

        verify(redis).delete("login_attempts:1.1.1.1:alice");
        verify(redis).delete("login_blocked:1.1.1.1:alice");
    }

    @Test
    @DisplayName("null/blank IP → fallback 'unknown' в ключ (не падает)")
    void nullIp_fallbackUnknown() {
        when(valueOps.increment(anyString())).thenReturn(1L);

        limiter.recordFailure(null, "alice");
        limiter.recordFailure("", "bob");

        verify(valueOps).increment("login_attempts:unknown:alice");
        verify(valueOps).increment("login_attempts:unknown:bob");
    }
}
