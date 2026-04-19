package ru.rutcampustrack.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRateLimiterConfigTest {

    private final RedisRateLimiterConfig config = new RedisRateLimiterConfig();

    @Test
    @DisplayName("ipKeyResolver: X-Forwarded-For первый IP используется как ключ")
    void ipResolver_usesForwardedForFirstIp() {
        KeyResolver resolver = config.ipKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x")
                .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("203.0.113.5")
                .verifyComplete();
    }

    @Test
    @DisplayName("ipKeyResolver: без X-Forwarded-For fallback на 'unknown' (no remote addr in mock)")
    void ipResolver_fallbackWhenNoForwardedFor() {
        KeyResolver resolver = config.ipKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        StepVerifier.create(resolver.resolve(exchange))
                .expectNext("unknown")
                .verifyComplete();
    }

    @Test
    @DisplayName("userIdKeyResolver: X-User-Id → ключ 'user:<id>'")
    void userIdResolver_usesHeader() {
        KeyResolver resolver = config.userIdKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x")
                .header("X-User-Id", "42")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("user:42")
                .verifyComplete();
    }

    @Test
    @DisplayName("userIdKeyResolver: без X-User-Id fallback на IP")
    void userIdResolver_fallbackToIp() {
        KeyResolver resolver = config.userIdKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x")
                .header("X-Forwarded-For", "198.51.100.7")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("ip:198.51.100.7")
                .verifyComplete();
    }

    @Test
    @DisplayName("loginKeyResolver: X-Login нормализуется в lowercase")
    void loginResolver_lowercases() {
        KeyResolver resolver = config.loginKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x")
                .header("X-Login", "Student01")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("login:student01")
                .verifyComplete();
    }

    @Test
    @DisplayName("loginKeyResolver: пустой header → fallback на IP")
    void loginResolver_fallbackToIp() {
        KeyResolver resolver = config.loginKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x")
                .header("X-Login", "")
                .header("X-Forwarded-For", "203.0.113.9")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("ip:203.0.113.9")
                .verifyComplete();
    }

    @Test
    @DisplayName("ipLoginKeyResolver: composite key — ip:<ip>:login:<login>")
    void ipLoginResolver_compositeKey() {
        KeyResolver resolver = config.ipLoginKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.5")
                .header("X-Login", "Teacher02")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("ip:203.0.113.5:login:teacher02")
                .verifyComplete();
    }

    @Test
    @DisplayName("ipLoginKeyResolver: разные IP с одним login дают РАЗНЫЕ ключи")
    void ipLoginResolver_differentIpsNotCollapsed() {
        KeyResolver resolver = config.ipLoginKeyResolver();
        MockServerHttpRequest r1 = MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "1.1.1.1").header("X-Login", "admin").build();
        MockServerHttpRequest r2 = MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "2.2.2.2").header("X-Login", "admin").build();

        String k1 = resolver.resolve(MockServerWebExchange.from(r1)).block();
        String k2 = resolver.resolve(MockServerWebExchange.from(r2)).block();
        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1).isEqualTo("ip:1.1.1.1:login:admin");
        assertThat(k2).isEqualTo("ip:2.2.2.2:login:admin");
    }

    @Test
    @DisplayName("ipLoginKeyResolver: отсутствие login → fallback только на IP (без :login:)")
    void ipLoginResolver_fallbackToIpOnly() {
        KeyResolver resolver = config.ipLoginKeyResolver();
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login")
                .header("X-Forwarded-For", "203.0.113.5")
                .build();

        StepVerifier.create(resolver.resolve(MockServerWebExchange.from(req)))
                .expectNext("ip:203.0.113.5")
                .verifyComplete();
    }
}
