package ru.rutcampustrack.gateway.ratelimit;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * M03a Группа 10: обогащает ответ 429 RFC 7807 Problem Details body +
 * {@code Retry-After: 60} (окно лимита — 60 секунд).
 *
 * <p>{@code RequestRateLimiterGatewayFilterFactory} сам по-себе пишет только
 * статус 429 + {@code X-RateLimit-*} headers, без body. Этот filter оборачивает
 * response до того, как RL-фильтр записывает ответ, и подставляет Problem Details
 * JSON-тело первым {@code writeWith}, если статус 429.</p>
 *
 * <p>Order: между {@link ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter}
 * (-100) / {@link ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter}
 * (-50) и route-filters (которые идут по пайплайну дальше).</p>
 */
@Component
public class RateLimitProblemDetailsFilter implements GlobalFilter, Ordered {

    private static final String BODY_TEMPLATE = """
            {"type":"https://ruttrack.site/problems/rate-limit-exceeded",\
            "title":"Too Many Requests",\
            "status":429,\
            "detail":"Request rate limit exceeded. Retry later."}""";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    HttpHeaders headers = getHeaders();
                    headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                    if (!headers.containsKey(HttpHeaders.RETRY_AFTER)) {
                        headers.set(HttpHeaders.RETRY_AFTER, "60");
                    }
                    byte[] payload = BODY_TEMPLATE.getBytes(StandardCharsets.UTF_8);
                    headers.setContentLength(payload.length);
                    DataBuffer buffer = bufferFactory().wrap(payload);
                    // swallow original empty body from RequestRateLimiter
                    return Flux.from(body).ignoreElements().then(super.writeWith(Mono.just(buffer)));
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMap(p -> p));
            }
        };

        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Run ПЕРЕД route-filters (включая RequestRateLimiter) чтобы декорировать
        // response раньше, чем RL запишет туда статус.
        return -40;
    }
}
