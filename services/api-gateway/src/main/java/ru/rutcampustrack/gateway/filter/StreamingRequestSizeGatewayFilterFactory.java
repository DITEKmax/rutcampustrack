package ru.rutcampustrack.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enforces a byte cap while the request body is streamed. Unlike the built-in
 * RequestSize filter this also counts chunked requests with no Content-Length.
 */
@Component
public class StreamingRequestSizeGatewayFilterFactory
        extends AbstractGatewayFilterFactory<StreamingRequestSizeGatewayFilterFactory.Config> {

    public static final long DEFAULT_MAX_BYTES = 25_165_824L;

    public StreamingRequestSizeGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        long maxBytes = config.getMaxBytes();
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("StreamingRequestSize maxBytes must be positive");
        }
        return (exchange, chain) -> filter(exchange, chain, maxBytes);
    }

    private Mono<Void> filter(ServerWebExchange exchange,
                               GatewayFilterChain chain,
                               long maxBytes) {
        ServerHttpRequest request = exchange.getRequest();
        long contentLength = request.getHeaders().getContentLength();
        if (contentLength > maxBytes) {
            return tooLarge(exchange, maxBytes);
        }
        if (contentLength >= 0) {
            return chain.filter(exchange);
        }

        AtomicLong seen = new AtomicLong();
        Flux<DataBuffer> limitedBody = Flux.defer(request::getBody)
                .<DataBuffer>handle((buffer, sink) -> {
                    long next;
                    try {
                        next = Math.addExact(seen.get(), buffer.readableByteCount());
                    } catch (ArithmeticException overflow) {
                        DataBufferUtils.release(buffer);
                        sink.error(new RequestTooLargeException(maxBytes));
                        return;
                    }
                    if (next > maxBytes) {
                        DataBufferUtils.release(buffer);
                        sink.error(new RequestTooLargeException(maxBytes));
                        return;
                    }
                    seen.set(next);
                    sink.next(buffer);
                })
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release);

        ServerHttpRequest decorated = new org.springframework.http.server.reactive.ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return limitedBody;
            }
        };
        ServerWebExchange mutated = exchange.mutate().request(decorated).build();
        return chain.filter(mutated)
                .onErrorResume(RequestTooLargeException.class,
                        ignored -> tooLarge(exchange, maxBytes));
    }

    private Mono<Void> tooLarge(ServerWebExchange exchange, long maxBytes) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        String body = "{\"type\":\"https://ruttrack.site/problems/request-too-large\","
                + "\"title\":\"Payload Too Large\",\"status\":413,"
                + "\"detail\":\"Request body exceeds " + maxBytes + " bytes.\"}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(payload.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    public static class Config {
        private long maxBytes = DEFAULT_MAX_BYTES;

        public long getMaxBytes() {
            return maxBytes;
        }

        public Config setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
            return this;
        }
    }

    private static final class RequestTooLargeException extends RuntimeException {
        private RequestTooLargeException(long maxBytes) {
            super("Request body exceeds " + maxBytes + " bytes");
        }
    }
}
