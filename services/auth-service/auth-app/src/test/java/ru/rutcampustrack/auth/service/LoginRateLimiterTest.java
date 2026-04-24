package ru.rutcampustrack.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // M03b Группа 9 (KI-6): INCR+EXPIRE слиты в Lua. Stub для execute(..., keys, args).
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubLuaIncrement(String key, long returnCount) {
        when(redis.execute(any(RedisScript.class), eq(List.of(key)), anyString()))
                .thenReturn(returnCount);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void verifyLuaIncrementCalled(String key) {
        verify(redis).execute(any(RedisScript.class), eq(List.of(key)), anyString());
    }

    @Test
    @DisplayName("recordFailure: key формируется как login_attempts:<ip>:<login>")
    void recordFailure_compositeKey() {
        stubLuaIncrement("login_attempts:1.2.3.4:alice", 1L);

        limiter.recordFailure("1.2.3.4", "alice");

        verifyLuaIncrementCalled("login_attempts:1.2.3.4:alice");
    }

    @Test
    @DisplayName("recordFailure: разные IP с одним login → разные ключи, НЕ аккумулируются")
    void recordFailure_differentIpsIsolated() {
        stubLuaIncrement("login_attempts:1.1.1.1:alice", 1L);
        stubLuaIncrement("login_attempts:2.2.2.2:alice", 1L);

        limiter.recordFailure("1.1.1.1", "alice");
        limiter.recordFailure("2.2.2.2", "alice");

        verifyLuaIncrementCalled("login_attempts:1.1.1.1:alice");
        verifyLuaIncrementCalled("login_attempts:2.2.2.2:alice");
    }

    @Test
    @DisplayName("recordFailure: 5 попыток → блок на 5 минут")
    void recordFailure_5thAttempt_blocks5Min() {
        stubLuaIncrement("login_attempts:ip:login", 5L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("recordFailure: 10 попыток → блок на 30 минут")
    void recordFailure_10thAttempt_blocks30Min() {
        stubLuaIncrement("login_attempts:ip:login", 10L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("recordFailure: 20 попыток → блок на 2 часа + delete счётчика")
    void recordFailure_20thAttempt_blocks2Hours() {
        stubLuaIncrement("login_attempts:ip:login", 20L);

        limiter.recordFailure("ip", "login");

        verify(valueOps).set(eq("login_blocked:ip:login"), eq("1"), eq(Duration.ofHours(2)));
        verify(redis).delete("login_attempts:ip:login");
    }

    @Test
    @DisplayName("recordFailure: 4 попыток → без блока")
    void recordFailure_belowThreshold_noBlock() {
        stubLuaIncrement("login_attempts:ip:login", 4L);

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
        stubLuaIncrement("login_attempts:unknown:alice", 1L);
        stubLuaIncrement("login_attempts:unknown:bob", 1L);

        limiter.recordFailure(null, "alice");
        limiter.recordFailure("", "bob");

        verifyLuaIncrementCalled("login_attempts:unknown:alice");
        verifyLuaIncrementCalled("login_attempts:unknown:bob");
    }
}
