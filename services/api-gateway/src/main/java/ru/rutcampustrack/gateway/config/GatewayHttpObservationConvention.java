package ru.rutcampustrack.gateway.config;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.server.reactive.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

/** Final HTTP measurements distinguish caller cancellation from a completed response. */
@Component
public class GatewayHttpObservationConvention extends DefaultServerRequestObservationConvention {
    @Override
    protected KeyValue uri(ServerRequestObservationContext context) {
        Object route = context.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        Object matchedRouteId =
                context.getAttributes()
                        .get(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR);
        Object template =
                context.getAttributes()
                        .get(ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR);
        if (route instanceof Route matched
                && matched.getId().equals(matchedRouteId)
                && template instanceof String path) {
            return KeyValue.of("uri", path);
        }
        return super.uri(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        Object route = context.getAttributes().get(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route instanceof Route matched ? matched.getId() : "unmatched";
        String termination =
                context.isConnectionAborted()
                        ? "cancelled"
                        : context.getError() != null ? "error" : "completed";
        return super.getLowCardinalityKeyValues(context)
                .and("routeId", routeId)
                .and("termination", termination);
    }
}
