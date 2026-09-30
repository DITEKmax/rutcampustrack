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
    @DisplayName("Local password-reset verify 429 → contract code, retry window and no-store")
    void status429_passwordResetVerifyLocalDenialUsesContractBody() {
        MockServerHttpRequest req = MockServerHttpRequest.post("/api/auth/password-reset/verify").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return decoratedExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        MockServerHttpResponse response = exchange.getResponse();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String body = response.getBodyAsString().block();
        assertThat(body)
                .contains("\"status\":429")
                .contains("\"code\":\"OTP_RATE_LIMITED\"")
                .contains("\"retryAfterSeconds\":12")
                .doesNotContain("rate-limit-exceeded");
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
    @DisplayName("Status 429 через setComplete() (RequestRateLimiter path) → Problem Details body")
    void status429_viaSetComplete_writesBody() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(req);

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            // simulate RequestRateLimiter: just setComplete, no writeWith
            return decoratedExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        MockServerHttpResponse resp = exchange.getResponse();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        String body = resp.getBodyAsString().block();
        assertThat(body).contains("\"status\":429").contains("\"title\":\"Too Many Requests\"");
    }

    @Test
    @DisplayName("Backend 429 → body и account retry/no-store headers сохраняются")
    void status429_preservesBackendProblemDetailsAndHeaders() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/x").build();
        ServerWebExchange exchange = MockServerWebExchange.from(req);
        String backendBody = """
                {"status":429,"extras":{"code":"OTP_RATE_LIMITED","retryAfterSeconds":37}}""";

        GatewayFilterChain chain = decoratedExchange -> {
            decoratedExchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            decoratedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            decoratedExchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, "37");
            decoratedExchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            DataBuffer buf = bufferFactory.wrap(backendBody.getBytes(StandardCharsets.UTF_8));
            return decoratedExchange.getResponse().writeWith(Mono.just(buf));
        };

        filter.filter(exchange, chain).block();

        MockServerHttpResponse response = (MockServerHttpResponse) exchange.getResponse();
        assertThat(response.getBodyAsString().block()).isEqualTo(backendBody);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("37");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }
}
