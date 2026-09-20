package ru.rutcampustrack.gateway.clientip;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Makes the raw socket peer the only trust decision point for forwarding
 * headers. The canonical address is available to rate limiters as an internal
 * exchange attribute and is also forwarded as a single sanitized IP header.
 */
@Component
public class TrustedClientIpFilter implements GlobalFilter, Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String FORWARDED = "Forwarded";
    private static final List<String> FORWARDING_HEADERS = List.of(
            X_FORWARDED_FOR,
            "X-Real-IP",
            "X-Forwarded-Proto",
            "X-Forwarded-Host",
            "X-Forwarded-Port",
            FORWARDED
    );

    private final TrustedClientIpResolver resolver;

    public TrustedClientIpFilter(TrustedClientIpResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        TrustedClientIpResolver.Resolution resolution = resolver.resolve(exchange.getRequest());
        String trustedProto = resolution.trustedPeer()
                ? singleSafeForwardedValue(exchange.getRequest().getHeaders(), "X-Forwarded-Proto", "https", "http")
                : null;
        String trustedHost = resolution.trustedPeer()
                ? singleSafeForwardedValue(exchange.getRequest().getHeaders(), "X-Forwarded-Host")
                : null;
        String trustedPort = resolution.trustedPeer()
                ? singleSafeForwardedValue(exchange.getRequest().getHeaders(), "X-Forwarded-Port")
                : null;

        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(headers -> {
                    FORWARDING_HEADERS.forEach(headers::remove);
                    headers.set(X_FORWARDED_FOR, resolution.clientIp());
                    headers.set("X-Real-IP", resolution.clientIp());
                    if (trustedProto != null) {
                        headers.set("X-Forwarded-Proto", trustedProto);
                    }
                    if (trustedHost != null) {
                        headers.set("X-Forwarded-Host", trustedHost);
                    }
                    if (trustedPort != null) {
                        headers.set("X-Forwarded-Port", trustedPort);
                    }
                })
                .build();

        exchange.getAttributes().put(TrustedClientIpResolver.CLIENT_IP_ATTRIBUTE, resolution.clientIp());
        exchange.getAttributes().put(TrustedClientIpResolver.PEER_IP_ATTRIBUTE, resolution.peerIp());
        ServerWebExchange normalized = exchange.mutate().request(sanitized).build();
        return chain.filter(normalized);
    }

    private static String singleSafeForwardedValue(HttpHeaders headers, String name, String... allowed) {
        List<String> values = headers.getValuesAsList(name);
        if (values.size() != 1) {
            return null;
        }
        String value = values.get(0).strip();
        if (value.isEmpty() || value.indexOf(',') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            return null;
        }
        if (allowed.length > 0) {
            for (String candidate : allowed) {
                if (candidate.equals(value)) {
                    return value;
                }
            }
            return null;
        }
        return value;
    }

    @Override
    public int getOrder() {
        return -300;
    }
}
