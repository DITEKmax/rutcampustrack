package ru.rutcampustrack.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * M03a: runs after {@link ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter}.
 * Reads the decoded user claims (already injected as X-User-* headers by the upstream
 * filter) and calls {@link InternalJwtIssuerClient} to obtain a signed Internal JWT,
 * which is then added as {@code X-Internal-Token} to the downstream request.
 *
 * <p>Dual-mode (NEW-4 / DECISIONS 2026-04-19): X-User-* legacy headers are NOT stripped.
 * Downstream {@code DualModeUserContextFilter} accepts either header family. After UAT,
 * a follow-up commit enables strict mode (strip X-User-* on the gateway side).</p>
 */
@Component
public class InternalJwtIssuerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalJwtIssuerFilter.class);
    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalJwtIssuerClient client;

    public InternalJwtIssuerFilter(InternalJwtIssuerClient client) {
        this.client = client;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userIdHeader = request.getHeaders().getFirst("X-User-Id");
        String role = request.getHeaders().getFirst("X-User-Role");

        // No upstream identity → nothing to exchange. Public routes or pre-auth requests.
        if (userIdHeader == null || userIdHeader.isBlank() || role == null || role.isBlank()) {
            return chain.filter(exchange);
        }

        long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            log.warn("X-User-Id is not numeric: {}", userIdHeader);
            return chain.filter(exchange);
        }

        Long groupId = parseLongOrNull(request.getHeaders().getFirst("X-Group-Id"));
        boolean isHeadman = Boolean.parseBoolean(request.getHeaders().getFirst("X-Is-Headman"));

        return client.issueFor(userId, role, groupId, isHeadman)
                .flatMap(token -> {
                    ServerHttpRequest mutated = request.mutate()
                            .header(INTERNAL_TOKEN_HEADER, token)
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .onErrorResume(InternalIssuerUnavailableException.class, e -> {
                    log.warn("Internal JWT issuer unavailable: {}", e.getMessage());
                    return serviceUnavailable(exchange);
                });
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Mono<Void> serviceUnavailable(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"status\":503,\"title\":\"Service Unavailable\","
                + "\"detail\":\"Internal token issuer is currently unavailable\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // JwtAuthenticationFilter is at -100; we need to run AFTER it so that X-User-* are set.
        return -50;
    }
}
