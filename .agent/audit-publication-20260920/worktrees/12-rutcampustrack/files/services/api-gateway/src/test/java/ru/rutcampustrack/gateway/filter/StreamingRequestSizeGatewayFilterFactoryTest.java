package ru.rutcampustrack.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingRequestSizeGatewayFilterFactoryTest {

    private final StreamingRequestSizeGatewayFilterFactory factory =
            new StreamingRequestSizeGatewayFilterFactory();

    @Test
    void factoryUsesExactTransportDefault() {
        assertThat(factory.newConfig().getMaxBytes()).isEqualTo(25_165_824L);
        assertThat(factory.name()).isEqualTo("StreamingRequestSize");
    }

    @Test
    void knownOversizeIsRejectedBeforeDownstreamSubscription() {
        StreamingRequestSizeGatewayFilterFactory.Config config = factory.newConfig().setMaxBytes(3);
        GatewayFilter filter = factory.apply(config);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/upload")
                .header(HttpHeaders.CONTENT_LENGTH, "4")
                .build());
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, ignored -> {
            calls.incrementAndGet();
            return Mono.empty();
        }).block();

        assertThat(calls).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void chunkedBodyIsCountedAndOverflowMapsTo413() {
        StreamingRequestSizeGatewayFilterFactory.Config config = factory.newConfig().setMaxBytes(3);
        GatewayFilter filter = factory.apply(config);
        DefaultDataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        DataBuffer first = buffers.wrap(new byte[]{1, 2});
        DataBuffer second = buffers.wrap(new byte[]{3, 4});
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/upload")
                .body(Flux.just(first, second)));
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger successfulCompletions = new AtomicInteger();

        filter.filter(exchange, downstream -> {
            calls.incrementAndGet();
            return DataBufferUtils.join(downstream.getRequest().getBody())
                    .doOnNext(DataBufferUtils::release)
                    .then()
                    .doOnSuccess(ignored -> successfulCompletions.incrementAndGet());
        }).block();

        assertThat(calls).hasValue(1);
        assertThat(successfulCompletions).hasValue(0);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void chunkedBodyAtLimitReachesDownstream() {
        StreamingRequestSizeGatewayFilterFactory.Config config = factory.newConfig().setMaxBytes(4);
        GatewayFilter filter = factory.apply(config);
        DefaultDataBufferFactory buffers = DefaultDataBufferFactory.sharedInstance;
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/upload")
                .body(Flux.just(buffers.wrap(new byte[]{1, 2}), buffers.wrap(new byte[]{3, 4}))));
        AtomicInteger calls = new AtomicInteger();

        filter.filter(exchange, downstream -> {
            calls.incrementAndGet();
            return DataBufferUtils.join(downstream.getRequest().getBody())
                    .doOnNext(DataBufferUtils::release)
                    .then();
        }).block();

        assertThat(calls).hasValue(1);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
