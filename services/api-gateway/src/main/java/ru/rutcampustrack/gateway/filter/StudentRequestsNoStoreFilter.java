package ru.rutcampustrack.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Applies the Requests route cache policy before auth, rate limiting and routing. */
@Component
public class StudentRequestsNoStoreFilter implements GlobalFilter, Ordered {

    public static final String REQUESTS_PATH = "/api/v1/student/requests";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isRequestsPath(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }

        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    static boolean isRequestsPath(String path) {
        return REQUESTS_PATH.equals(path) || (path != null && path.startsWith(REQUESTS_PATH + "/"));
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
