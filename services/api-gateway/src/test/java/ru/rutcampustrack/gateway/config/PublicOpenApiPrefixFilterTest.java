package ru.rutcampustrack.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;

class PublicOpenApiPrefixFilterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicOpenApiPrefixFilter filter =
            new PublicOpenApiPrefixFilter(
                    new ModifyResponseBodyGatewayFilterFactory(
                            ServerCodecConfigurer.create().getReaders(), Set.of(), Set.of()),
                    mapper);

    @Test
    void publicDocumentUsesExternalPrefixWithoutChangingWirePathsOrSecurity() throws Exception {
        String original =
                "{\"openapi\":\"3.1.0\",\"servers\":[{\"url\":\"http://private:8080\"}],\"paths\":{\"/auth/exports\":{\"post\":{\"responses\":{\"202\":{}},\"security\":[{\"bearerAuth\":[]}]}}}}";
        for (String service : new String[] {"auth-service", "notification-service", "mobile-bff"}) {
            var exchange =
                    MockServerWebExchange.from(MockServerHttpRequest.get("/openapi/" + service));
            filter.filter(
                            exchange,
                            current -> {
                                current.getResponse()
                                        .setStatusCode(org.springframework.http.HttpStatus.OK);
                                current.getResponse()
                                        .getHeaders()
                                        .setContentType(MediaType.APPLICATION_JSON);
                                return current.getResponse()
                                        .writeWith(
                                                Mono.just(
                                                        current.getResponse()
                                                                .bufferFactory()
                                                                .wrap(
                                                                        original.getBytes(
                                                                                StandardCharsets
                                                                                        .UTF_8))));
                            })
                    .block();
            var actual = mapper.readTree(exchange.getResponse().getBodyAsString().block());
            assertThat(actual.path("servers").path(0).path("url").asText())
                    .isEqualTo(service.equals("mobile-bff") ? "/" : "/api");
            assertThat(actual.path("paths")).isEqualTo(mapper.readTree(original).path("paths"));
        }
    }

    @Test
    void upstreamNonJsonFailureKeepsItsStatusAndUsefulBody() {
        var exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/openapi/academic-service"));
        filter.filter(
                        exchange,
                        current -> {
                            current.getResponse()
                                    .setStatusCode(
                                            org.springframework.http.HttpStatus
                                                    .SERVICE_UNAVAILABLE);
                            current.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                            return current.getResponse()
                                    .writeWith(
                                            Mono.just(
                                                    current.getResponse()
                                                            .bufferFactory()
                                                            .wrap(
                                                                    "upstream unavailable"
                                                                            .getBytes(
                                                                                    StandardCharsets
                                                                                            .UTF_8))));
                        })
                .block();
        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("upstream unavailable");
    }

    @Test
    void ordinaryResponseAndNonDocumentErrorRemainUnchanged() throws Exception {
        var body = mapper.readTree("{\"status\":503}");
        assertThat(PublicOpenApiPrefixFilter.withPublicServer(body, "/api")).isSameAs(body);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/session"));
        filter.filter(
                        exchange,
                        current ->
                                current.getResponse()
                                        .writeWith(
                                                Mono.just(
                                                        current.getResponse()
                                                                .bufferFactory()
                                                                .wrap(
                                                                        "unchanged"
                                                                                .getBytes(
                                                                                        StandardCharsets
                                                                                                .UTF_8)))))
                .block();
        assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("unchanged");
    }
}
