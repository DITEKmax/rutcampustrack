package ru.rutcampustrack.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class StudentRequestsNoStoreFilterTest {

    private final StudentRequestsNoStoreFilter filter = new StudentRequestsNoStoreFilter();

    @Test
    void exactRouteAndDescendantsAreNoStoreBeforeDownstream() {
        for (String path : new String[]{
                "/api/v1/student/requests", "/api/v1/student/requests/excuse", "/api/v1/student/requests/excuse/status"}) {
            var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            AtomicReference<String> observed = new AtomicReference<>();
            GatewayFilterChain chain = ex -> {
                observed.set(ex.getResponse().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL));
                return Mono.empty();
            };

            filter.filter(exchange, chain).block();

            assertThat(observed).hasValue("no-store");
            assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        }
    }

    @Test
    void neighboringPathIsNotMatched() {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/student/requests-old").build());

        filter.filter(exchange, ignored -> Mono.empty()).block();

        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CACHE_CONTROL)).isNull();
    }

    @Test
    void orderIsBeforeAuthentication() {
        assertThat(filter.getOrder()).isEqualTo(-200);
    }
}
