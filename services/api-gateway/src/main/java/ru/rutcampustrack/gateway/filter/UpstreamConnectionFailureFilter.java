package ru.rutcampustrack.gateway.filter;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.shared.observability.MdcKeys;

/** Reports an unavailable routed dependency without retrying the original request. */
@Component
public class UpstreamConnectionFailureFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        URI requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        if (route == null
                || !("attendance-service".equals(route.getId())
                        || "schedule-service".equals(route.getId()))
                || requestUrl == null
                || !("http".equals(requestUrl.getScheme())
                        || "https".equals(requestUrl.getScheme()))) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange)
                .onErrorResume(
                        ConnectException.class,
                        error -> {
                            if (exchange.getResponse().isCommitted()) {
                                return Mono.error(error);
                            }
                            return unavailable(exchange);
                        });
    }

    private static Mono<Void> unavailable(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().setCacheControl("no-store");
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String traceField =
                traceId != null && traceId.matches("[0-9a-f]{16}|[0-9a-f]{32}")
                        ? ",\"traceId\":\"" + traceId + "\""
                        : "";
        String body =
                "{\"status\":503,\"title\":\"Service Unavailable\","
                        + "\"detail\":\"Dependency unavailable\","
                        + "\"code\":\"DEPENDENCY_UNAVAILABLE\""
                        + traceField
                        + "}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        response.getHeaders().setContentLength(payload.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)));
    }

    @Override
    public int getOrder() {
        return NettyRoutingFilter.ORDER - 1;
    }
}
