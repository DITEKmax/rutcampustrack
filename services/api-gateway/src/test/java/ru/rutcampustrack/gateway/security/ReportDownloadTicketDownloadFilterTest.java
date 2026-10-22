package ru.rutcampustrack.gateway.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.*;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.*;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

class ReportDownloadTicketDownloadFilterTest {
    private static final String TICKET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";

    private ReportDownloadAttemptRateLimiter budget(boolean allowed) {
        var budget = mock(ReportDownloadAttemptRateLimiter.class);
        when(budget.tryAcquire(anyString())).thenReturn(Mono.just(allowed));
        return budget;
    }

    private GatewayFilterChain chain() {
        return exchange -> Mono.error(new AssertionError("exact ticket must be handled"));
    }

    private ClientResponse file() {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "text/html")
                .header("Content-Disposition", "attachment; filename=teacher-journal.html")
                .body("<html>committed</html>")
                .build();
    }

    @Test
    void fixedForwardingDoesNotCarryCallerCredentialsSelectorOrDispatch() {
        var seen = new AtomicReference<ClientRequest>();
        var client =
                WebClient.builder()
                        .baseUrl("http://academic-service:9091")
                        .exchangeFunction(
                                request -> {
                                    seen.set(request);
                                    return Mono.just(file());
                                })
                        .build();
        var filter = new ReportDownloadTicketDownloadFilter(client, budget(true));
        var exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/report-download/" + TICKET)
                                .header("Authorization", "Bearer caller")
                                .header("Cookie", "session=caller")
                                .header("X-Internal-Token", "caller-controlled")
                                .header("Origin", "https://web.telegram.org"));
        filter.filter(exchange, chain()).block(Duration.ofSeconds(2));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("<html>committed</html>");
        assertThat(seen.get().url().toString())
                .isEqualTo("http://academic-service:9091/academic/report-download/" + TICKET);
        assertThat(seen.get().headers())
                .doesNotContainKeys("Authorization", "Cookie", "X-Internal-Token");
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("https://web.telegram.org");
    }

    @Test
    void overrideRangeMalformedAndExhaustedRequestsNeverReachOwner() {
        var transport = mock(ExchangeFunction.class);
        var client = WebClient.builder().exchangeFunction(transport).build();
        var filter = new ReportDownloadTicketDownloadFilter(client, budget(true));
        for (var request :
                new MockServerHttpRequest.BaseBuilder<?>[] {
                    MockServerHttpRequest.get("/api/report-download/" + TICKET + "?format=pdf"),
                    MockServerHttpRequest.get("/api/report-download/" + TICKET)
                            .header("Range", "bytes=0-4"),
                    MockServerHttpRequest.post("/api/report-download/" + TICKET),
                    MockServerHttpRequest.get("/api/report-download/not-a-ticket")
                }) {
            var exchange = MockServerWebExchange.from(request.build());
            filter.filter(exchange, chain()).block(Duration.ofSeconds(2));
            assertThat(exchange.getResponse().getStatusCode().is4xxClientError()).isTrue();
            assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        }
        var denied = new ReportDownloadTicketDownloadFilter(client, budget(false));
        var exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/report-download/" + TICKET));
        denied.filter(exchange, chain()).block(Duration.ofSeconds(2));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verifyNoInteractions(transport);
    }

    @Test
    void ownerDenialAndMalformedOrOversizeArtifactsFailClosedWithoutLeakingBody() {
        var replies =
                java.util.List.of(
                        ClientResponse.create(HttpStatus.FORBIDDEN)
                                .body("private owner diagnostic")
                                .build(),
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .header(
                                        "Content-Disposition",
                                        "attachment; filename=teacher-journal.html")
                                .body("private owner diagnostic")
                                .build(),
                        ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "text/html")
                                .header(
                                        "Content-Disposition",
                                        "attachment; filename=teacher-journal.html")
                                .header("Content-Length", "20971521")
                                .body("private owner diagnostic")
                                .build());
        for (var reply : replies) {
            var client =
                    WebClient.builder()
                            .baseUrl("http://academic-service:9091")
                            .exchangeFunction(request -> Mono.just(reply))
                            .build();
            var filter = new ReportDownloadTicketDownloadFilter(client, budget(true));
            var exchange =
                    MockServerWebExchange.from(
                            MockServerHttpRequest.get("/api/report-download/" + TICKET));
            filter.filter(exchange, chain()).block(Duration.ofSeconds(2));
            assertThat(exchange.getResponse().getStatusCode().value()).isIn(403, 503);
            assertThat(exchange.getResponse().getBodyAsString().block())
                    .doesNotContain("private owner diagnostic", TICKET);
        }
    }
}
