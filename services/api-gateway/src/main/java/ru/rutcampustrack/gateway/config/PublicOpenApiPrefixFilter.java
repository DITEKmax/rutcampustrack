package ru.rutcampustrack.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

import java.util.Set;

/** Changes only the public view of the existing service document, never its API paths. */
@Component
public final class PublicOpenApiPrefixFilter implements GlobalFilter, Ordered {
    private static final Set<String> SERVICES =
            Set.of(
                    "/openapi/auth-service",
                    "/openapi/academic-service",
                    "/openapi/schedule-service",
                    "/openapi/attendance-service",
                    "/openapi/notification-service");
    private final ModifyResponseBodyGatewayFilterFactory responses;
    private final ObjectMapper mapper;

    public PublicOpenApiPrefixFilter(
            ModifyResponseBodyGatewayFilterFactory responses, ObjectMapper mapper) {
        this.responses = responses;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String prefix =
                SERVICES.contains(path) ? "/api" : "/openapi/mobile-bff".equals(path) ? "/" : null;
        if (prefix == null) return chain.filter(exchange);
        var config =
                new ModifyResponseBodyGatewayFilterFactory.Config()
                        .setRewriteFunction(
                                String.class,
                                String.class,
                                (current, body) -> {
                                    if (current.getResponse().getStatusCode() != null
                                            && !current.getResponse()
                                                    .getStatusCode()
                                                    .is2xxSuccessful()) {
                                        return Mono.justOrEmpty(body);
                                    }
                                    return Mono.justOrEmpty(rewrite(body, prefix));
                                });
        return responses.apply(config).filter(exchange, chain);
    }

    private String rewrite(String original, String prefix) {
        if (original == null) return null;
        try {
            JsonNode source = mapper.readTree(original);
            JsonNode rewritten = withPublicServer(source, prefix);
            return source == rewritten ? original : mapper.writeValueAsString(rewritten);
        } catch (JsonProcessingException invalidDocument) {
            return original;
        }
    }

    static JsonNode withPublicServer(JsonNode document, String prefix) {
        if (document instanceof ObjectNode object && object.has("openapi") && object.has("paths")) {
            ObjectNode copy = object.deepCopy();
            var servers = JsonNodeFactory.instance.arrayNode();
            servers.addObject().put("url", prefix);
            copy.set("servers", servers);
            return copy;
        }
        return document;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }
}
