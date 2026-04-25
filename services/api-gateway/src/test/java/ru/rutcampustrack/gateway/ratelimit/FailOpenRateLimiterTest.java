package ru.rutcampustrack.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FailOpenRateLimiterTest {

    private final RedisRateLimiter delegate = mock(RedisRateLimiter.class);
    private final ConfigurationService configurationService = mock(ConfigurationService.class);
    private final FailOpenRateLimiter limiter = new FailOpenRateLimiter(delegate, configurationService);

    @Test
    @DisplayName("Delegate allowed=true → проходит, headers stripped (G25.22)")
    void delegateAllowed_passThrough() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.just(new RateLimiter.Response(true, Map.of("X-RateLimit-Remaining", "5"))));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .assertNext(r -> {
                    assertThat(r.isAllowed()).isTrue();
                    // M13 G25.22 — headers всегда пусты (workaround Spring Cloud Gateway 4.x bug
                    // ReadOnlyHttpHeaders.add после committed response). См. FailOpenRateLimiter.
                    assertThat(r.getHeaders()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Delegate allowed=false (normal rate-limit rejection) → остаётся false, headers stripped")
    void delegateDenied_remainsDenied() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.just(new RateLimiter.Response(false, Map.of("X-RateLimit-Remaining", "0"))));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .assertNext(r -> {
                    assertThat(r.isAllowed()).isFalse();
                    assertThat(r.getHeaders()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("RedisConnectionFailureException → fail-open (allowed=true, headers пусты)")
    void redisConnectionFailure_failsOpen() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.error(new RedisConnectionFailureException("Redis down")));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .assertNext(r -> {
                    assertThat(r.isAllowed()).isTrue();
                    assertThat(r.getHeaders()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("QueryTimeoutException → fail-open")
    void queryTimeout_failsOpen() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.error(new QueryTimeoutException("slow")));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .assertNext(r -> assertThat(r.isAllowed()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("Lettuce RedisConnectionException → fail-open")
    void lettuceConnectionException_failsOpen() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.error(new io.lettuce.core.RedisConnectionException("no redis")));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .assertNext(r -> assertThat(r.isAllowed()).isTrue())
                .verifyComplete();
    }

    @Test
    @DisplayName("Unknown non-Redis exception (IllegalStateException) → пробрасывается, НЕ fail-open")
    void unknownException_propagates() {
        when(delegate.isAllowed(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("bug in code")));

        StepVerifier.create(limiter.isAllowed("route-1", "key-1"))
                .expectError(IllegalStateException.class)
                .verify();
    }
}
