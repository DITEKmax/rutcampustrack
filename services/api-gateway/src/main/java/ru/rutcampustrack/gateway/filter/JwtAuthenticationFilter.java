package ru.rutcampustrack.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/public-key",
            "/api/auth/tma",
            "/api/auth/refresh-body",
            "/swagger-ui.html"
    );

    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/api/auth/otp/",
            "/api/ws/",
            "/swagger-ui/",
            "/v3/api-docs",
            "/openapi/"
    );

    private final PublicKeyConfig publicKeyConfig;

    public JwtAuthenticationFilter(PublicKeyConfig publicKeyConfig) {
        this.publicKeyConfig = publicKeyConfig;
    }

    private static final List<String> INTERNAL_HEADERS = List.of(
            // Legacy X-User-* (dual-mode legacy identity) — client-supplied injection blocked.
            "X-User-Id", "X-User-Role", "X-Group-Id", "X-Is-Headman",
            // M03a post-audit: block client-supplied Internal JWT injection.
            // Without this strip, a compromised sibling pod on the docker private-net
            // could forward its own X-Internal-Token directly to downstream, bypassing
            // Gateway's token-exchange. Gateway ALWAYS issues X-Internal-Token itself
            // in InternalJwtIssuerFilter → strip any client-provided value first.
            "X-Internal-Token",
            // M03a post-audit: block client-supplied X-Login injection into composite
            // rate-limit key. Clients that need rate-limit isolation must send login
            // in request body; Gateway may populate X-Login internally (future enhancement).
            "X-Login"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // CRIT-01: Strip client-supplied internal headers to prevent privilege escalation
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> INTERNAL_HEADERS.forEach(h::remove))
                .build();
        exchange = exchange.mutate().request(sanitized).build();

        String path = exchange.getRequest().getURI().getPath();

        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKeyConfig.getPublicKey())
                    .requireIssuer("rutcampustrack-auth")
                    .requireAudience("rutcampustrack")
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object groupId = claims.get("group_id");
            Object isHeadman = claims.get("is_headman");

            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                    .header("X-User-Id", claims.getSubject())
                    .header("X-User-Role", claims.get("role", String.class));

            if (groupId != null) {
                requestBuilder.header("X-Group-Id", String.valueOf(groupId));
            }
            if (isHeadman != null) {
                requestBuilder.header("X-Is-Headman", String.valueOf(isHeadman));
            }

            ServerHttpRequest mutatedRequest = requestBuilder.build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return unauthorized(exchange, "Invalid or expired JWT token");
        }
    }

    private boolean isPublicRoute(String path) {
        if (PUBLIC_PATHS.contains(path)) return true;
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        String body = """
                {"status":401,"title":"Unauthorized","detail":"%s"}
                """.formatted(detail).strip();
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
