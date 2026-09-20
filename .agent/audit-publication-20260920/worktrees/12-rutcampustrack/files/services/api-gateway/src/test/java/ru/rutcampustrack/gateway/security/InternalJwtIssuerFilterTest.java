package ru.rutcampustrack.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;
import ru.rutcampustrack.gateway.filter.JwtAuthenticationFilter;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InternalJwtIssuerFilterTest {

    private static final String ACCESS_TOKEN = "validated-access-token";
    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000001";

    private KeyPair keyPair;
    private InternalJwtIssuerClient client;
    private PublicKeyConfig publicKeyConfig;
    private InternalJwtIssuerFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        client = mock(InternalJwtIssuerClient.class);
        publicKeyConfig = mock(PublicKeyConfig.class);
        when(publicKeyConfig.getPublicKey()).thenReturn(keyPair.getPublic());
        filter = new InternalJwtIssuerFilter(client, publicKeyConfig);
    }

    @Test
    void validAdmission_forwardsSignedInternalTokenAndSanitizesLegacyIdentity() {
        AuthAdmissionResponse response = validAdmission();
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = exchangeWithExpectations();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedChain(forwarded);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst("X-Internal-Token")).isEqualTo(response.internalToken());
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Login")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Group-Id"));
        assertThat(forwarded.get().getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Is-Headman"));
        assertThat((String) forwarded.get().getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE))
                .isEqualTo("42");
    }

    @Test
    void validAdmission_withoutGroupId_acceptsAbsentOptionalGroupId() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Instant tokenExpiry = accessExpiry.minusSeconds(60);
        String token = signedRawToken(validClaims(tokenExpiry));
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response(token, tokenExpiry)));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedChain(forwarded);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
    }

    @Test
    void explicitNullInternalGroupId_returns503WithoutDownstreamCall() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Instant tokenExpiry = accessExpiry.minusSeconds(60);
        Map<String, Object> claims = validClaims(tokenExpiry);
        claims.put("group_id", null);
        String token = signedRawToken(claims);
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response(token, tokenExpiry)));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingAccessExpectations_returns503WithoutAuthorityCall() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/academic/users").build());
        exchange.getAttributes().put(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE,
                ACCESS_TOKEN);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(client);
    }

    @Test
    void authorityDenial_returnsTypedPublicResponseWithoutDownstreamCall() {
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.error(
                new InternalAdmissionDeniedException(HttpStatus.FORBIDDEN, "WRONG_ROLE")));
        MockServerWebExchange exchange = exchangeWithExpectations();
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(chain, publicKeyConfig);
    }

    @Test
    void malformedInternalTimeClaim_returns503WithoutDownstreamCall() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Map<String, Object> claims = validClaims(accessExpiry.minusSeconds(60));
        claims.put("iat", "not-a-number");
        String token = signedRawToken(claims);
        AuthAdmissionResponse response = response(token, accessExpiry.minusSeconds(60));
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void fractionalInternalExpiration_returns503WithoutDownstreamCall() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Instant tokenExpiry = accessExpiry.minusSeconds(60);
        Map<String, Object> claims = validClaims(tokenExpiry);
        claims.put("exp", BigDecimal.valueOf(tokenExpiry.getEpochSecond()).add(BigDecimal.valueOf(0.5)));
        String token = signedRawToken(claims);
        AuthAdmissionResponse response = response(token, tokenExpiry);
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void downstreamFailure_isPropagatedWithOriginalIdentity() {
        AuthAdmissionResponse response = validAdmission();
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = exchangeWithExpectations();
        IllegalStateException downstreamFailure = new IllegalStateException("downstream sentinel");
        GatewayFilterChain chain = ex -> Mono.error(downstreamFailure);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(downstreamFailure))
                .verify();

        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void noAccessTokenAttribute_passesThroughWithoutAuthorityCall() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = forwardedChain(forwarded);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isSameAs(exchange);
        verifyNoInteractions(client);
    }

    @Test
    void filterOrder_isAfterJwtAuth() {
        assertThat(filter.getOrder()).isGreaterThan(-100);
    }

    private AuthAdmissionResponse validAdmission() {
        Instant tokenExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 60);
        return response(signedJjwtToken(tokenExpiry), tokenExpiry);
    }

    private AuthAdmissionResponse response(String internalToken, Instant expiresAt) {
        return new AuthAdmissionResponse(internalToken, expiresAt, SESSION_ID, "42", "7", "3",
                "STUDENT", "ACTIVE", null, false, false);
    }

    private MockServerWebExchange exchangeWithExpectations() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        return exchangeWithExpectations(accessExpiry);
    }

    private MockServerWebExchange exchangeWithExpectations(Instant accessExpiry) {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/academic/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .header("X-User-Id", "666")
                .header("X-User-Role", "ADMIN")
                .header("X-Login", "forged")
                .header("x-GrOuP-Id", "666")
                .header("X-iS-hEaDmAn", "true")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE,
                ACCESS_TOKEN);
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_USER_ID_ATTRIBUTE, "42");
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_SESSION_ID_ATTRIBUTE, SESSION_ID);
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_SESSION_VERSION_ATTRIBUTE, "7");
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_ROLES_VERSION_ATTRIBUTE, "3");
        exchange.getAttributes().put(JwtAuthenticationFilter.ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE,
                accessExpiry);
        return exchange;
    }

    private static GatewayFilterChain forwardedChain(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    private String signedJjwtToken(Instant expiry) {
        Instant issuedAt = expiry.minusSeconds(60);
        return Jwts.builder()
                .subject("42")
                .issuer("rutcampustrack-auth")
                .audience().add("rutcampustrack-internal").and()
                .claim("token_use", "internal")
                .claim("sid", SESSION_ID)
                .claim("sv", "7")
                .claim("rv", "3")
                .claim("role", "STUDENT")
                .claim("status", "ACTIVE")
                .claim("is_headman", false)
                .claim("readOnly", false)
                .issuedAt(java.util.Date.from(issuedAt))
                .expiration(java.util.Date.from(expiry))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static Map<String, Object> validClaims(Instant expiry) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "42");
        claims.put("iss", "rutcampustrack-auth");
        claims.put("aud", "rutcampustrack-internal");
        claims.put("iat", expiry.getEpochSecond() - 60);
        claims.put("exp", expiry.getEpochSecond());
        claims.put("token_use", "internal");
        claims.put("sid", SESSION_ID);
        claims.put("sv", "7");
        claims.put("rv", "3");
        claims.put("role", "STUDENT");
        claims.put("status", "ACTIVE");
        claims.put("is_headman", false);
        claims.put("readOnly", false);
        return claims;
    }

    private String signedRawToken(Map<String, Object> claims) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            String payload = encoder.encodeToString(new ObjectMapper().writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign((PrivateKey) keyPair.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + encoder.encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign raw JWT test fixture", e);
        }
    }
}
