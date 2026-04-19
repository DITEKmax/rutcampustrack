package ru.rutcampustrack.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitProblemDetailsFilterTest {

    private final RateLimitProblemDetailsFilter filter = new RateLimitProblemDetailsFilter();
    private final DefaultDataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    @Test
    @DisplayName("Status 200 → body passes through без модификации")
    void status200_passThrough() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.OK);
            DataBuffer buf = bufferFactory.wrap("hello".getBytes(StandardCharsets.UTF_8));
            return decoratedExchange.getResponse().writeWith(Mono.just(buf));
        };

        filter.filter(exchange, chain).block();

        MockServerHttpResponse resp = exchange.getResponse();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = resp.getBodyAsString().block();
        assertThat(body).isEqualTo("hello");
    }

    @Test
    @DisplayName("Status 429 → body заменяется Problem Details + Retry-After + Content-Type")
    void status429_rewritesBody() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            // simulate RequestRateLimiter — empty body
            return decoratedExchange.getResponse().writeWith(Mono.empty());
        };

        filter.filter(exchange, chain).block();

        MockServerHttpResponse resp = exchange.getResponse();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        String body = resp.getBodyAsString().block();
        assertThat(body)
                .contains("\"type\":\"https://ruttrack.site/problems/rate-limit-exceeded\"")
                .contains("\"status\":429")
                .contains("\"title\":\"Too Many Requests\"")
                .contains("\"detail\":");
    }

    @Test
    @DisplayName("Status 429 + уже установленный Retry-After → не перезаписывается")
    void status429_retryAfterAlreadySet_preserved() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            decoratedExchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "120");
            return decoratedExchange.getResponse().writeWith(Mono.empty());
        };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo("120");
    }

    @Test
    @DisplayName("Order = -40 (после JwtAuthenticationFilter -100, перед route-filters)")
    void orderValue() {
        assertThat(filter.getOrder()).isEqualTo(-40);
    }

    @Test
    @DisplayName("Status 429 с предыдущим непустым body → оригинальное body игнорируется")
    void status429_swallowsPreviousBody() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x").build();
        ServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            DataBuffer buf = bufferFactory.wrap("old-body".getBytes(StandardCharsets.UTF_8));
            return decoratedExchange.getResponse().writeWith(Mono.just(buf));
        };

        filter.filter(exchange, chain).block();

        String body = ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
        assertThat(body).doesNotContain("old-body");
        assertThat(body).contains("\"status\":429");
    }
}
