package ru.rutcampustrack.gateway.clientip;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedClientIpFilterTest {

    @Test
    void trustedEdgeNormalizesIdentityAndForwardingHeaders() throws Exception {
        TrustedClientIpFilter filter = new TrustedClientIpFilter(resolver("192.0.2.10"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("192.0.2.10"))
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Real-IP", "198.51.100.7")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "ruttrack.site")
                .header("X-Forwarded-Port", "443")
                .header("Forwarded", "for=198.51.100.7")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        ServerWebExchange normalized = captured.get();
        assertThat(normalized.getRequest().getHeaders().get("X-Forwarded-For"))
                .containsExactly("203.0.113.7");
        assertThat(normalized.getRequest().getHeaders().getFirst("X-Real-IP"))
                .isEqualTo("203.0.113.7");
        assertThat(normalized.getRequest().getHeaders().getFirst("Forwarded")).isNull();
        assertThat((String) normalized.getAttribute(TrustedClientIpResolver.CLIENT_IP_ATTRIBUTE))
                .isEqualTo("203.0.113.7");
        assertThat((String) normalized.getAttribute(TrustedClientIpResolver.PEER_IP_ATTRIBUTE))
                .isEqualTo("192.0.2.10");
        assertThat(normalized.getRequest().getHeaders().getFirst("X-Forwarded-Proto"))
                .isEqualTo("https");
    }

    @Test
    void untrustedPeerCannotSelectForwardingIdentityOrTrustedMetadata() throws Exception {
        TrustedClientIpFilter filter = new TrustedClientIpFilter(resolver("192.0.2.10"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .remoteAddress(address("198.51.100.9"))
                .header("X-Forwarded-For", "203.0.113.7")
                .header("X-Real-IP", "203.0.113.8")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "attacker.invalid")
                .header("X-Forwarded-Port", "443")
                .header("Forwarded", "for=203.0.113.7")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(exchange, ex -> {
            captured.set(ex);
            return Mono.empty();
        }).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-Forwarded-For")).isEqualTo("198.51.100.9");
        assertThat(headers.getFirst("X-Real-IP")).isEqualTo("198.51.100.9");
        assertThat(headers.getFirst("X-Forwarded-Proto")).isNull();
        assertThat(headers.getFirst("X-Forwarded-Host")).isNull();
        assertThat(headers.getFirst("X-Forwarded-Port")).isNull();
        assertThat(headers.getFirst("Forwarded")).isNull();
    }

    @Test
    void orderRunsBeforeAuthAndRequestsFilters() {
        assertThat(new TrustedClientIpFilter(resolver("192.0.2.10")).getOrder()).isEqualTo(-300);
    }

    private static TrustedClientIpResolver resolver(String address) {
        TrustedClientIpProperties properties = new TrustedClientIpProperties();
        properties.setTrustedProxyAddresses(List.of(address));
        properties.setRequired(true);
        properties.validateAndInitialize();
        return new TrustedClientIpResolver(properties);
    }

    private static InetSocketAddress address(String value) throws Exception {
        return new InetSocketAddress(InetAddress.getByName(value), 8080);
    }
}
