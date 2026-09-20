package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LoginBodyExtractionFilterTest {

    private final LoginBodyExtractionFilter filter = new LoginBodyExtractionFilter(new ObjectMapper());

    @Test
    void validBodyIsReplayedExactlyOnceAndLoginIsNormalized() {
        String body = "{\"login\":\" İUser \"}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                .body(body);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        AtomicReference<byte[]> replay = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), exchange -> {
            calls.incrementAndGet();
            captured.set(exchange);
            return DataBufferUtils.join(exchange.getRequest().getBody()).doOnNext(buffer -> {
                byte[] bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                replay.set(bytes);
                DataBufferUtils.release(buffer);
            }).then();
        }).block();

        assertThat(calls).hasValue(1);
        assertThat(replay.get()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Login"))
                .isEqualTo("i̇user");
        assertThat(captured.get().getRequest().getHeaders().getContentLength())
                .isEqualTo(body.getBytes(StandardCharsets.UTF_8).length);
        assertThat(captured.get().getRequest().getHeaders().getFirst(HttpHeaders.TRANSFER_ENCODING))
                .isNull();
    }

    @Test
    void emptyAndMalformedBodiesReachDownstreamOnce() {
        AtomicInteger emptyCalls = new AtomicInteger();
        AtomicReference<ServerWebExchange> emptyExchange = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").build()), exchange -> {
            emptyCalls.incrementAndGet();
            emptyExchange.set(exchange);
            return Mono.empty();
        }).block();

        AtomicInteger malformedCalls = new AtomicInteger();
        AtomicReference<ServerWebExchange> malformedExchange = new AtomicReference<>();
        filter.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login").body("{bad")), exchange -> {
            malformedCalls.incrementAndGet();
            malformedExchange.set(exchange);
            return Mono.empty();
        }).block();

        assertThat(emptyCalls).hasValue(1);
        assertThat(emptyExchange.get().getRequest().getHeaders().getContentLength()).isZero();
        assertThat(malformedCalls).hasValue(1);
        assertThat(malformedExchange.get().getRequest().getHeaders().getFirst("X-Login")).isNull();
    }

    @Test
    void knownOversizeBodyReturns413WithoutDownstreamCall() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .header(HttpHeaders.CONTENT_LENGTH, "4097")
                .build();
        AtomicInteger calls = new AtomicInteger();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, ignored -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(calls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void chunkedOversizeBodyReturns413WithoutDownstreamCall() {
        DefaultDataBufferFactory factory = DefaultDataBufferFactory.sharedInstance;
        DataBuffer first = factory.wrap(new byte[4096]);
        DataBuffer second = factory.wrap(new byte[]{1});
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/login")
                .body(reactor.core.publisher.Flux.just(first, second));
        AtomicInteger calls = new AtomicInteger();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        filter.filter(exchange, ignored -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(calls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void orderIsAfterJwtAndBeforeInternalIssuer() {
        assertThat(filter.getOrder()).isEqualTo(-90);
    }
}
