package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * M03b Группа 9 (KI-8): извлекает {@code login} из JSON body POST
 * {@code /api/auth/login} и ставит header {@code X-Login} для composite
 * {@code (ip, login)} rate-limit.
 *
 * <p>Работает ПОСЛЕ {@link JwtAuthenticationFilter} (order -100), который
 * strip'ает клиентский {@code X-Login} — значит наш header гарантированно
 * внутренний. Order {@code -90} ставит фильтр до
 * {@code RequestRateLimiterGatewayFilterFactory} (он per-route, runs в
 * route filter chain после GlobalFilters).</p>
 *
 * <p>Тело кэшируется (DataBufferUtils.join + cache()) и прокидывается downstream
 * через ServerHttpRequestDecorator — Spring Cloud Gateway не поддерживает
 * прямой {@code CacheRequestBody} в GlobalFilter, поэтому реализация вручную.</p>
 */
@Component
public class LoginBodyExtractionFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoginBodyExtractionFilter.class);
    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String LOGIN_HEADER = "X-Login";
    private static final int MAX_BODY_BYTES = 4096;
    private static final String TOO_LARGE_BODY =
            "{\"type\":\"https://ruttrack.site/problems/request-too-large\","
                    + "\"title\":\"Payload Too Large\",\"status\":413,"
                    + "\"detail\":\"Login request body exceeds 4096 bytes.\"}";

    private final ObjectMapper objectMapper;

    public LoginBodyExtractionFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!HttpMethod.POST.equals(request.getMethod())
                || !LOGIN_PATH.equals(request.getURI().getPath())) {
            return chain.filter(exchange);
        }

        if (request.getHeaders().getContentLength() > MAX_BODY_BYTES) {
            return tooLarge(exchange);
        }

        return DataBufferUtils.join(request.getBody(), MAX_BODY_BYTES)
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    try {
                        buffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    String login = extractLogin(bytes);
                    ServerHttpRequestDecorator decorated = cachedBodyDecorator(request, bytes, login);
                    ServerWebExchange mutated = exchange.mutate().request(decorated).build();
                    return chain.filter(mutated);
                })
                .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
                .onErrorResume(DataBufferLimitException.class, ignored -> tooLarge(exchange));
    }

    private String extractLogin(byte[] bodyBytes) {
        if (bodyBytes.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(bodyBytes);
            JsonNode loginNode = root == null ? null : root.get("login");
            if (loginNode != null && loginNode.isTextual()) {
                String login = loginNode.asText().strip().toLowerCase(Locale.ROOT);
                if (!login.isBlank() && login.length() <= 100) {
                    return login;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse /auth/login body for X-Login extraction: {}", e.getMessage());
        }
        return null;
    }

    private ServerHttpRequestDecorator cachedBodyDecorator(ServerHttpRequest original,
                                                           byte[] bodyBytes,
                                                           String login) {
        return new ServerHttpRequestDecorator(original) {
            @Override
            public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(super.getHeaders());
                headers.remove(HttpHeaders.TRANSFER_ENCODING);
                if (login != null) {
                    headers.set(LOGIN_HEADER, login);
                }
                headers.setContentLength(bodyBytes.length);
                return headers;
            }

            @Override
            public Flux<DataBuffer> getBody() {
                DataBuffer fresh = new DefaultDataBufferFactory().wrap(bodyBytes);
                return Flux.just(fresh);
            }
        };
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private Mono<Void> tooLarge(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        byte[] payload = TOO_LARGE_BODY.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(payload.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }
}
