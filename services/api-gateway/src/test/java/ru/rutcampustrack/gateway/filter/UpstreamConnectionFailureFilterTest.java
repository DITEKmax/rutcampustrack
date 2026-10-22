package ru.rutcampustrack.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.rutcampustrack.shared.observability.MdcKeys;

class UpstreamConnectionFailureFilterTest {

    private final UpstreamConnectionFailureFilter filter = new UpstreamConnectionFailureFilter();

    @Test
    void routedGetAndPostConnectFailuresReturnTheFixedPrivate503WithoutRetry() throws Exception {
        for (String route : new String[] {"attendance-service", "schedule-service"}) {
            for (ConnectException error :
                    new ConnectException[] {
                        new ConnectException("private-marker"),
                        annotatedConnectException(),
                        new ConnectTimeoutException("private-marker"),
                        annotatedConnectException(new ConnectTimeoutException("private-marker"))
                    }) {
                var exchange = exchange(route, "https");
                AtomicInteger subscriptions = new AtomicInteger();
                filter.filter(
                                exchange,
                                ignored ->
                                        Mono.defer(
                                                () -> {
                                                    subscriptions.incrementAndGet();
                                                    return Mono.error(error);
                                                }))
                        .block();

                assertThat(subscriptions).hasValue(1);
                assertThat(exchange.getResponse().getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exchange.getResponse().getHeaders().getContentType())
                        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
                assertThat(exchange.getResponse().getHeaders().getCacheControl())
                        .isEqualTo("no-store");
                assertThat(exchange.getResponse().getBodyAsString().block())
                        .isEqualTo(
                                "{\"status\":503,\"title\":\"Service Unavailable\","
                                        + "\"detail\":\"Dependency unavailable\",\"code\":\"DEPENDENCY_UNAVAILABLE\"}");
            }
        }
    }

    @Test
    void anExistingValidTraceIsCorrelatedButUntrustedTextIsNeverReflected() throws Exception {
        String previous = MDC.get(MdcKeys.TRACE_ID);
        try {
            for (String trace :
                    new String[] {"0123456789abcdef0123456789abcdef", "private-marker\""}) {
                MDC.put(MdcKeys.TRACE_ID, trace);
                var exchange = exchange("attendance-service", "http");
                filter.filter(
                                exchange,
                                ignored -> Mono.error(new ConnectException("private-marker")))
                        .block();
                var body =
                        new ObjectMapper()
                                .readTree(exchange.getResponse().getBodyAsString().block());
                if (trace.length() == 32)
                    assertThat(body.path("traceId").asText()).isEqualTo(trace);
                else assertThat(body.has("traceId")).isFalse();
                assertThat(body.toString()).doesNotContain("private-marker");
            }
        } finally {
            if (previous == null) MDC.remove(MdcKeys.TRACE_ID);
            else MDC.put(MdcKeys.TRACE_ID, previous);
        }
    }

    @Test
    void completedBackendStatusesAndBodiesAreUnchanged() {
        for (int status : new int[] {200, 400, 401, 403, 404, 409, 429, 500, 503}) {
            var exchange = exchange("attendance-service", "http");
            filter.filter(
                            exchange,
                            ignored -> {
                                exchange.getResponse().setRawStatusCode(status);
                                return exchange.getResponse()
                                        .writeWith(
                                                Mono.just(
                                                        exchange.getResponse()
                                                                .bufferFactory()
                                                                .wrap(
                                                                        "backend"
                                                                                .getBytes(
                                                                                        StandardCharsets
                                                                                                .UTF_8))));
                            })
                    .block();
            assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(status);
            assertThat(exchange.getResponse().getBodyAsString().block()).isEqualTo("backend");
        }
    }

    @Test
    void alreadyTypedTimeoutAndUnrelatedErrorsRemainTheOriginalError() {
        for (Throwable error :
                new Throwable[] {
                    new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT),
                    new IllegalStateException(
                            "private-marker", new ConnectException("private-marker"))
                }) {
            var exchange = exchange("attendance-service", "http");
            StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(error)))
                    .expectErrorSatisfies(observed -> assertThat(observed).isSameAs(error))
                    .verify();
            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }

    @Test
    void otherRoutesAndNonHttpRoutingRemainUnchanged() {
        for (String route :
                new String[] {"auth-service", "academic-service", "attendance-excuse-upload"}) {
            assertOriginalError(exchange(route, "http"));
        }
        assertOriginalError(exchange("attendance-service", "ws"));
        var missingRoute = exchange("attendance-service", "http");
        missingRoute.getAttributes().remove(GATEWAY_ROUTE_ATTR);
        assertOriginalError(missingRoute);
    }

    @Test
    void committedResponsesAreNeverReplaced() {
        var exchange = exchange("attendance-service", "http");
        exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
        exchange.getResponse().setComplete().block();
        assertOriginalError(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    @Test
    void onlyTheLateRoutingChainIsWrapped() {
        assertThat(filter.getOrder()).isEqualTo(NettyRoutingFilter.ORDER - 1);
    }

    private void assertOriginalError(MockServerWebExchange exchange) {
        ConnectException error = new ConnectException("private-marker");
        StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(error)))
                .expectErrorSatisfies(observed -> assertThat(observed).isSameAs(error))
                .verify();
    }

    private static MockServerWebExchange exchange(String id, String scheme) {
        var request =
                "schedule-service".equals(id)
                        ? MockServerHttpRequest.post("/api/schedule/one-off-lessons")
                        : MockServerHttpRequest.get("/api/attendance/reports/lesson/1");
        var exchange = MockServerWebExchange.from(request.build());
        URI uri = URI.create(scheme + "://owned.invalid:9093");
        exchange.getAttributes()
                .put(
                        GATEWAY_ROUTE_ATTR,
                        Route.async().id(id).uri(uri).predicate(ignored -> true).build());
        exchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, uri);
        return exchange;
    }

    private static ConnectException annotatedConnectException() throws Exception {
        return annotatedConnectException(new ConnectException("private-marker"));
    }

    private static ConnectException annotatedConnectException(ConnectException cause)
            throws Exception {
        var constructor =
                Class.forName("io.netty.channel.AbstractChannel$AnnotatedConnectException")
                        .getDeclaredConstructor(ConnectException.class, SocketAddress.class);
        constructor.setAccessible(true);
        return (ConnectException)
                constructor.newInstance(
                        cause, InetSocketAddress.createUnresolved("owned.invalid", 9093));
    }
}
