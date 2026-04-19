package ru.rutcampustrack.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalJwtIssuerFilterTest {

    private InternalJwtIssuerClient client;
    private InternalIssuerClientProperties props;
    private InternalJwtIssuerFilter filter;

    @BeforeEach
    void setUp() {
        client = mock(InternalJwtIssuerClient.class);
        props = new InternalIssuerClientProperties();
        props.setSecret("a".repeat(32));
        props.setStripLegacyHeaders(false);  // default dual-mode
        filter = new InternalJwtIssuerFilter(client, props);
    }

    @Test
    void userHeadersPresent_addsXInternalToken() {
        when(client.issueFor(anyLong(), anyString(), any(), anyBoolean()))
                .thenReturn(Mono.just("signed-jwt-42"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/academic/users")
                .header("X-User-Id", "42")
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "7")
                .header("X-Is-Headman", "true")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Token"))
                .isEqualTo("signed-jwt-42");
        verify(client).issueFor(42L, "ADMIN", 7L, true);
    }

    @Test
    void noUserHeaders_skipsIssuer() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            captured.set(ex);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Token")).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void nonNumericUserId_skipsIssuer() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .header("X-User-Id", "not-a-number")
                .header("X-User-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verifyNoInteractions(client);
    }

    @Test
    void issuerUnavailable_returns503() {
        when(client.issueFor(anyLong(), anyString(), any(), anyBoolean()))
                .thenReturn(Mono.error(new InternalIssuerUnavailableException("auth-service down")));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/academic/users")
                .header("X-User-Id", "42")
                .header("X-User-Role", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not be invoked"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void nullGroupId_passesNullToIssuer() {
        when(client.issueFor(anyLong(), anyString(), any(), anyBoolean()))
                .thenReturn(Mono.just("t"));

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/x")
                .header("X-User-Id", "100")
                .header("X-User-Role", "TEACHER")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(client).issueFor(100L, "TEACHER", null, false);
    }

    @Test
    void filterOrder_isAfterJwtAuth() {
        // JwtAuthenticationFilter = -100; this one must come later (higher order number)
        assertThat(filter.getOrder()).isGreaterThan(-100);
    }

    @Test
    void dualMode_stripLegacyFalse_keepsXUserHeaders() {
        when(client.issueFor(anyLong(), anyString(), any(), anyBoolean()))
                .thenReturn(Mono.just("signed-jwt"));
        props.setStripLegacyHeaders(false);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/academic/users")
                .header("X-User-Id", "42")
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "7")
                .header("X-Is-Headman", "true")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { captured.set(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        var hs = captured.get().getRequest().getHeaders();
        assertThat(hs.getFirst("X-Internal-Token")).isEqualTo("signed-jwt");
        assertThat(hs.getFirst("X-User-Id")).isEqualTo("42");
        assertThat(hs.getFirst("X-User-Role")).isEqualTo("ADMIN");
        assertThat(hs.getFirst("X-Group-Id")).isEqualTo("7");
        assertThat(hs.getFirst("X-Is-Headman")).isEqualTo("true");
    }

    @Test
    void strictMode_stripLegacyTrue_removesXUserHeaders() {
        when(client.issueFor(anyLong(), anyString(), any(), anyBoolean()))
                .thenReturn(Mono.just("signed-jwt"));
        props.setStripLegacyHeaders(true);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/academic/users")
                .header("X-User-Id", "42")
                .header("X-User-Role", "ADMIN")
                .header("X-Group-Id", "7")
                .header("X-Is-Headman", "true")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> { captured.set(ex); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        var hs = captured.get().getRequest().getHeaders();
        assertThat(hs.getFirst("X-Internal-Token")).isEqualTo("signed-jwt");
        assertThat(hs.getFirst("X-User-Id")).isNull();
        assertThat(hs.getFirst("X-User-Role")).isNull();
        assertThat(hs.getFirst("X-Group-Id")).isNull();
        assertThat(hs.getFirst("X-Is-Headman")).isNull();
    }
}
