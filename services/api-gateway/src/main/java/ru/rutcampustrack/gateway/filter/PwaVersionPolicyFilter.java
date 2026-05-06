package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PwaVersionPolicyFilter implements GlobalFilter, Ordered {

    static final String PWA_VERSION_HEADER = "X-PWA-Version";
    static final String PWA_LATEST_HEADER = "X-PWA-Latest-Version";
    static final String PWA_MINIMUM_SUPPORTED_HEADER = "X-PWA-Minimum-Supported-Version";

    private final PwaVersionPolicyProperties properties;
    private final ObjectMapper objectMapper;

    public PwaVersionPolicyFilter(PwaVersionPolicyProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()
                || exchange.getRequest().getMethod() == HttpMethod.OPTIONS
                || !exchange.getRequest().getURI().getPath().startsWith("/api/")) {
            return chain.filter(exchange);
        }

        String clientVersion = exchange.getRequest().getHeaders().getFirst(PWA_VERSION_HEADER);
        if (!StringUtils.hasText(clientVersion)) {
            return chain.filter(exchange);
        }

        if (!isUpdateRequired(clientVersion.trim())) {
            return chain.filter(exchange);
        }

        return upgradeRequired(exchange);
    }

    private boolean isUpdateRequired(String clientVersion) {
        String latest = trimToNull(properties.getLatest());
        if (properties.isForce() && latest != null) {
            return !clientVersion.equals(latest);
        }

        String minimumSupported = trimToNull(properties.effectiveMinimumSupported());
        return minimumSupported != null && compareVersions(clientVersion, minimumSupported) < 0;
    }

    private Mono<Void> upgradeRequired(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UPGRADE_REQUIRED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");

        String latest = trimToNull(properties.getLatest());
        String minimumSupported = trimToNull(properties.effectiveMinimumSupported());
        if (latest != null) {
            response.getHeaders().set(PWA_LATEST_HEADER, latest);
        }
        if (minimumSupported != null) {
            response.getHeaders().set(PWA_MINIMUM_SUPPORTED_HEADER, minimumSupported);
        }

        byte[] payload = problemDetailsPayload(latest, minimumSupported);
        response.getHeaders().setContentLength(payload.length);
        DataBuffer buffer = response.bufferFactory().wrap(payload);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] problemDetailsPayload(String latest, String minimumSupported) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "https://ruttrack.site/problems/pwa-upgrade-required");
        body.put("title", "Upgrade Required");
        body.put("status", 426);
        body.put("detail", "Эта версия RutTrack больше не поддерживается. Обновите PWA, чтобы продолжить.");

        Map<String, String> extras = new LinkedHashMap<>();
        if (latest != null) {
            extras.put("latest", latest);
        }
        if (minimumSupported != null) {
            extras.put("minimumSupported", minimumSupported);
        }
        if (!extras.isEmpty()) {
            body.put("extras", extras);
        }

        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            return "{\"status\":426,\"title\":\"Upgrade Required\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static int compareVersions(String left, String right) {
        int[] a = parseVersion(left);
        int[] b = parseVersion(right);
        int length = Math.max(a.length, b.length);

        for (int index = 0; index < length; index += 1) {
            int av = index < a.length ? a[index] : 0;
            int bv = index < b.length ? b[index] : 0;
            if (av > bv) return 1;
            if (av < bv) return -1;
        }
        return 0;
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("[.+-]");
        int[] result = new int[parts.length];
        for (int index = 0; index < parts.length; index += 1) {
            try {
                result[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                result[index] = 0;
            }
        }
        return result;
    }

    @Override
    public int getOrder() {
        return -110;
    }
}
