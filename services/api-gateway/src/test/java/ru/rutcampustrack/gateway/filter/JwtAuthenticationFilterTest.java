package ru.rutcampustrack.gateway.filter;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private static KeyPair keyPair;
    private static JwtAuthenticationFilter filter;
    private static PublicKeyConfig publicKeyConfig;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        publicKeyConfig = mock(PublicKeyConfig.class);
        when(publicKeyConfig.getPublicKey()).thenReturn(keyPair.getPublic());

        filter = new JwtAuthenticationFilter(publicKeyConfig);
    }

    private String generateToken(PrivateKey privateKey, Map<String, Object> claims, Date expiry) {
        var builder = Jwts.builder()
                .subject("123")
                .issuer("rutcampustrack-auth")
                .audience().add("rutcampustrack").and()
                .expiration(expiry)
                .signWith(privateKey);
        claims.forEach(builder::claim);
        return builder.compact();
    }

    // Test 1 (FR-9.1): Public route /api/auth/login passes through without JWT
    @Test
    void publicRoute_login_passesThroughWithoutJwt() {
        var request = MockServerHttpRequest.get("/api/auth/login").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    // Test 2 (FR-9.1): Public route /api/auth/otp/verify passes through without JWT
    @Test
    void publicRoute_otp_passesThroughWithoutJwt() {
        var request = MockServerHttpRequest.get("/api/auth/otp/verify").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    // Test 3 (FR-7.5): Missing Authorization header returns 401 with RFC 7807 body
    @Test
    void missingAuthorizationHeader_returns401() {
        var request = MockServerHttpRequest.get("/api/academic/groups").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // Test 4 (FR-7.5): Authorization header without "Bearer " prefix returns 401
    @Test
    void authHeaderWithoutBearerPrefix_returns401() {
        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Basic sometoken")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // Test 5 (FR-7.3): Valid JWT on protected route — chain.filter called with X-User-Id and X-User-Role headers
    @Test
    void validJwt_protectedRoute_injectsUserIdAndRoleHeaders() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken(keyPair.getPrivate(), Map.of("role", "STUDENT"), expiry);

        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        var mutated = captor.getValue().getRequest();
        assertThat(mutated.getHeaders().getFirst("X-User-Id")).isEqualTo("123");
        assertThat(mutated.getHeaders().getFirst("X-User-Role")).isEqualTo("STUDENT");
    }

    // Test 6 (FR-7.3): Valid JWT with group_id claim — X-Group-Id header present
    @Test
    void validJwt_withGroupId_injectsGroupIdHeader() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken(keyPair.getPrivate(),
                Map.of("role", "STUDENT", "group_id", 42L, "is_headman", false), expiry);

        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        var mutated = captor.getValue().getRequest();
        assertThat(mutated.getHeaders().getFirst("X-Group-Id")).isNotNull();
        assertThat(mutated.getHeaders().getFirst("X-Group-Id")).isEqualTo("42");
    }

    // Test 7 (FR-7.3): Valid JWT without group_id (TEACHER/ADMIN) — NO X-Group-Id header
    @Test
    void validJwt_withoutGroupId_doesNotInjectGroupIdHeader() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken(keyPair.getPrivate(), Map.of("role", "TEACHER"), expiry);

        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        var mutated = captor.getValue().getRequest();
        assertThat(mutated.getHeaders().getFirst("X-Group-Id")).isNull();
    }

    // Test 8 (FR-7.3): Expired JWT returns 401 with RFC 7807 body
    @Test
    void expiredJwt_returns401() {
        Date pastExpiry = new Date(System.currentTimeMillis() - 60_000);
        String token = generateToken(keyPair.getPrivate(), Map.of("role", "STUDENT"), pastExpiry);

        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // Test 9 (FR-7.5): Malformed JWT string returns 401
    @Test
    void malformedJwt_returns401() {
        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    // Test 10 (INFRA-01): OPTIONS preflight to /api/academic/groups passes through without JWT
    @Test
    void optionsRequest_passesThroughWithoutJwt() {
        var request = MockServerHttpRequest.options("/api/academic/groups").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Test 11 (INFRA-01): OPTIONS preflight to /api/push/subscribe passes through without JWT
    @Test
    void optionsRequest_pushRoute_passesThroughWithoutJwt() {
        var request = MockServerHttpRequest.options("/api/push/subscribe").build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Post-audit (M03a Группа 16): клиентские X-Internal-Token и X-Login
    // должны быть удалены до route-handling чтобы предотвратить header-injection.
    @Test
    void stripsClientSuppliedInternalTokenAndXLogin() {
        Date expiry = new Date(System.currentTimeMillis() + 60_000);
        String token = generateToken(keyPair.getPrivate(), Map.of("role", "STUDENT"), expiry);

        var request = MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Internal-Token", "attacker-crafted-jwt")
                .header("X-Login", "victim")
                .header("X-User-Id", "666")   // legacy attempt
                .build();
        var exchange = MockServerWebExchange.from(request);
        var chain = mock(GatewayFilterChain.class);
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        var mutated = captor.getValue().getRequest();
        // X-Internal-Token и X-Login должны быть удалены (header injection mitigation)
        assertThat(mutated.getHeaders().getFirst("X-Internal-Token")).isNull();
        assertThat(mutated.getHeaders().getFirst("X-Login")).isNull();
        // X-User-Id от клиента заменяется значением из JWT (sub=123)
        assertThat(mutated.getHeaders().getFirst("X-User-Id")).isEqualTo("123");
    }
}
