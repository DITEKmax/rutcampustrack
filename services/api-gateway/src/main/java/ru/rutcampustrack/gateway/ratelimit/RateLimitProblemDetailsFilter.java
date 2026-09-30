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
 * M03a Группа 10: обогащает локальный ответ 429 RFC 9457 Problem Details body.
 * Reset verify получает контрактный {@code OTP_RATE_LIMITED} detail и retry
 * window, рассчитанный по route-specific token cost; остальные local 429
 * сохраняют общий {@code Retry-After: 60}.
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
    private static final String PASSWORD_RESET_VERIFY_PATH = "/api/auth/password-reset/verify";
    // Keep aligned with auth-password-reset-verify in application.yml. A fully
    // depleted bucket needs 12 tokens; at 1 token/second the conservative refill is 12 seconds.
    private static final int PASSWORD_RESET_VERIFY_REPLENISH_RATE_PER_SECOND = 1;
    private static final int PASSWORD_RESET_VERIFY_REQUESTED_TOKENS = 12;
    private static final int PASSWORD_RESET_VERIFY_RETRY_AFTER_SECONDS =
            (PASSWORD_RESET_VERIFY_REQUESTED_TOKENS + PASSWORD_RESET_VERIFY_REPLENISH_RATE_PER_SECOND - 1)
                    / PASSWORD_RESET_VERIFY_REPLENISH_RATE_PER_SECOND;
    private static final String PASSWORD_RESET_VERIFY_BODY_TEMPLATE = """
            {"type":"https://api.rutcampustrack.ru/problems/password-reset-otp-rate-limited",\
            "title":"Password recovery request failed",\
            "status":429,\
            "detail":"The password recovery proof was denied",\
            "extras":{"code":"OTP_RATE_LIMITED","retryAfterSeconds":%d}}""";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return super.writeWith(Flux.<DataBuffer>from(body).switchIfEmpty(
                            Mono.defer(this::createProblemDetailsBuffer)));
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMap(p -> p));
            }

            @Override
            public Mono<Void> setComplete() {
                // RequestRateLimiterGatewayFilterFactory при denied вызывает setComplete()
                // без writeWith — пишем Problem Details здесь вместо пустого ответа.
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return writeProblemDetailsBody();
                }
                return super.setComplete();
            }

            private Mono<DataBuffer> createProblemDetailsBuffer() {
                HttpHeaders headers = getHeaders();
                headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                boolean isPasswordResetVerify = PASSWORD_RESET_VERIFY_PATH.equals(
                        exchange.getRequest().getURI().getPath());
                String body = BODY_TEMPLATE;
                if (isPasswordResetVerify) {
                    headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
                    headers.set(HttpHeaders.RETRY_AFTER,
                            Integer.toString(PASSWORD_RESET_VERIFY_RETRY_AFTER_SECONDS));
                    body = PASSWORD_RESET_VERIFY_BODY_TEMPLATE.formatted(
                            PASSWORD_RESET_VERIFY_RETRY_AFTER_SECONDS);
                } else if (!headers.containsKey(HttpHeaders.RETRY_AFTER)) {
                    headers.set(HttpHeaders.RETRY_AFTER, "60");
                }
                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                headers.setContentLength(payload.length);
                DataBuffer buffer = bufferFactory().wrap(payload);
                return Mono.just(buffer);
            }

            private Mono<Void> writeProblemDetailsBody() {
                return super.writeWith(Mono.defer(this::createProblemDetailsBuffer));
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
