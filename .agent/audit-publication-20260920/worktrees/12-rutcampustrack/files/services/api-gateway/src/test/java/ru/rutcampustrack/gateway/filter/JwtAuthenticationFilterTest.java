package ru.rutcampustrack.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000001";

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

    @Test
    void publicRoute_login_passesThroughWithoutJwt() {
        var exchange = exchange(MockServerHttpRequest.get("/api/auth/login").build());
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void publicRoute_otp_passesThroughWithoutJwt() {
        var exchange = exchange(MockServerHttpRequest.get("/api/auth/otp/verify").build());
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void missingAuthorizationHeader_returns401() {
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups").build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void authHeaderWithoutBearerPrefix_returns401() {
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Basic sometoken")
                .build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void validAccess_protectedRoute_setsPrivateExpectationsAndStripsIdentityHeaders() {
        String token = generateAccessToken(Map.of("role", "STUDENT"),
                futureExpiry());
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "666")
                .header("X-Internal-Token", "forged")
                .header("X-Login", "forged")
                .header("x-gRoUp-Id", "666")
                .header("X-iS-HeAdMaN", "true")
                .build());
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        var forwarded = captor.getValue();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Internal-Token")).isNull();
        assertThat(forwarded.getRequest().getHeaders().getFirst("X-Login")).isNull();
        assertThat(forwarded.getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Group-Id"));
        assertThat(forwarded.getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Is-Headman"));
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE))
                .isEqualTo(token);
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.EXPECTED_USER_ID_ATTRIBUTE))
                .isEqualTo("123");
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.EXPECTED_SESSION_ID_ATTRIBUTE))
                .isEqualTo(SESSION_ID);
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.EXPECTED_SESSION_VERSION_ATTRIBUTE))
                .isEqualTo("7");
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.EXPECTED_ROLES_VERSION_ATTRIBUTE))
                .isEqualTo("3");
        assertThat((Instant) forwarded.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE))
                .isInstanceOf(Instant.class);
        assertThat((String) forwarded.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE))
                .isNull();
    }

    @Test
    void validAccess_withOptionalGroupIdString_passesWithoutLegacyGroupHeader() {
        String token = generateAccessToken(Map.of("role", "STUDENT", "group_id", "42"),
                futureExpiry());
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat(captor.getValue().getRequest().getHeaders().getFirst("X-Group-Id")).isNull();
    }

    @Test
    void validAccess_withoutGroupId_passes() {
        String token = generateAccessToken(Map.of("role", "TEACHER"), futureExpiry());
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void validAccess_headmanRoleIsRecognizedWithoutLocalAuthorization() {
        String token = generateAccessToken(Map.of(
                "role", "HEADMAN",
                "status", "EXPELLED",
                "is_headman", true,
                "readOnly", true
        ), futureExpiry());
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
    }

    @Test
    void validAccess_onPostLogout_setsExpectationsForLiveAdmission() {
        String token = generateAccessToken(Map.of("role", "STUDENT"), futureExpiry());
        var exchange = exchange(MockServerHttpRequest.post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat((String) captor.getValue()
                .getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE))
                .isEqualTo(token);
    }

    @Test
    void missingOrMalformedBearer_onPostLogout_remainsIdempotentAndUnauthenticated() {
        assertLogoutPasses(MockServerHttpRequest.post("/api/auth/logout").build());
        assertLogoutPasses(MockServerHttpRequest.post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                .build());
    }

    @Test
    void validBootstrap_onAllowedLogoutPassesWithoutAdmissionExpectations() {
        String token = generateBootstrapToken();
        var exchange = exchange(MockServerHttpRequest.post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat((String) exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE))
                .isNull();
    }

    @Test
    void validBootstrap_onProtectedRoute_isDeniedBeforeAdmission() {
        String token = generateBootstrapToken();
        var exchange = exchange(MockServerHttpRequest.post("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    @Test
    void expiredAccess_returns401() {
        String token = generateAccessToken(Map.of("role", "STUDENT"), expiredExpiry());
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void malformedJwt_returns401() {
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt")
                .build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void optionsRequest_stripsClientIdentityHeadersAndPassesThrough() {
        var exchange = exchange(MockServerHttpRequest.options("/api/academic/groups")
                .header("X-User-Id", "999")
                .header("X-Internal-Token", "forged")
                .header("X-Login", "forged")
                .header("x-GROUP-id", "999")
                .header("x-is-HEADMAN", "true")
                .build());
        ArgumentCaptor<org.springframework.web.server.ServerWebExchange> captor =
                ArgumentCaptor.forClass(org.springframework.web.server.ServerWebExchange.class);
        var chain = mock(GatewayFilterChain.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        var headers = captor.getValue().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-Internal-Token")).isNull();
        assertThat(headers.getFirst("X-Login")).isNull();
        assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("X-Group-Id"));
        assertThat(headers.keySet()).noneMatch(name -> name.equalsIgnoreCase("X-Is-Headman"));
    }

    @Test
    void missingTokenUse_returns401() {
        Map<String, Object> claims = validAccessClaims();
        claims.remove("token_use");
        assertUnauthorized(signedRawToken(claims));
    }

    @Test
    void refreshTokenUse_returns401() {
        Map<String, Object> claims = validAccessClaims();
        claims.put("token_use", "refresh");
        assertUnauthorized(signedRawToken(claims));
    }

    @Test
    void invalidRole_returns401() {
        Map<String, Object> claims = validAccessClaims();
        claims.put("role", "OWNER");
        assertUnauthorized(signedRawToken(claims));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAccessClaims")
    void signedInvalidAccessClaims_areRejected(String reason, Map<String, Object> claims) {
        assertUnauthorized(reason, signedRawToken(claims));
    }

    private static Stream<Arguments> invalidAccessClaims() {
        return Stream.of(
                invalid("missing issuer", claims -> claims.remove("iss")),
                invalid("wrong issuer", claims -> claims.put("iss", "other-issuer")),
                invalid("wrong type issuer", claims -> claims.put("iss", 7L)),
                invalid("missing audience", claims -> claims.remove("aud")),
                invalid("wrong audience", claims -> claims.put("aud", "other-audience")),
                invalid("wrong type audience", claims -> claims.put("aud", 7L)),
                invalid("multiple audiences", claims ->
                        claims.put("aud", java.util.List.of("rutcampustrack", "other-audience"))),
                invalid("missing issued-at", claims -> claims.remove("iat")),
                invalid("string issued-at", claims -> claims.put("iat", "not-a-number")),
                invalid("non-whole issued-at", claims -> claims.put("iat",
                        BigDecimal.valueOf(Instant.now().minusSeconds(5).toEpochMilli() / 1000.0))),
                invalid("future issued-at", claims -> claims.put("iat",
                        Date.from(Instant.now().plusSeconds(60)))),
                invalid("missing expiration", claims -> claims.remove("exp")),
                invalid("string expiration", claims -> claims.put("exp", "not-a-number")),
                invalid("non-whole expiration", claims -> claims.put("exp",
                        BigDecimal.valueOf(Instant.now().plusSeconds(60).toEpochMilli() / 1000.0))),
                invalid("issued-at is not before expiration", claims -> {
                    Date expiry = (Date) claims.get("exp");
                    claims.put("iat", expiry);
                }),
                invalid("nonpositive subject", claims -> claims.put("sub", "0")),
                invalid("negative subject", claims -> claims.put("sub", "-1")),
                invalid("overflow subject", claims ->
                        claims.put("sub", "9223372036854775808")),
                invalid("missing subject", claims -> claims.remove("sub")),
                invalid("nonstring subject", claims -> claims.put("sub", 123L)),
                invalid("wrong type token use", claims -> claims.put("token_use", 1L)),
                invalid("missing session id", claims -> claims.remove("sid")),
                invalid("wrong session id", claims -> claims.put("sid", "not-a-uuid")),
                invalid("missing session version", claims -> claims.remove("sv")),
                invalid("missing roles version", claims -> claims.remove("rv")),
                invalid("missing role", claims -> claims.remove("role")),
                invalid("wrong type role", claims -> claims.put("role", 1L)),
                invalid("unknown role", claims -> claims.put("role", "OWNER")),
                invalid("missing status", claims -> claims.remove("status")),
                invalid("unknown status", claims -> claims.put("status", "DELETED")),
                invalid("wrong type group id", claims -> claims.put("group_id", 42L)),
                invalid("null group id", claims -> claims.put("group_id", null)),
                invalid("wrong type headman flag", claims -> claims.put("is_headman", "false")),
                invalid("missing read-only flag", claims -> claims.remove("readOnly")),
                invalid("wrong type read-only flag", claims -> claims.put("readOnly", "false"))
        );
    }

    private static Arguments invalid(String reason, Consumer<Map<String, Object>> mutation) {
        Map<String, Object> claims = validAccessClaims();
        mutation.accept(claims);
        return Arguments.of(reason, claims);
    }

    private static Map<String, Object> validAccessClaims() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "123");
        claims.put("iss", "rutcampustrack-auth");
        claims.put("aud", "rutcampustrack");
        claims.put("iat", Date.from(Instant.ofEpochSecond(now - 5)));
        claims.put("exp", Date.from(Instant.ofEpochSecond(now + 60)));
        claims.put("token_use", "access");
        claims.put("sid", SESSION_ID);
        claims.put("sv", "7");
        claims.put("rv", "3");
        claims.put("role", "STUDENT");
        claims.put("status", "ACTIVE");
        claims.put("is_headman", false);
        claims.put("readOnly", false);
        return claims;
    }

    private String generateAccessToken(Map<String, Object> overrides, Date expiry) {
        long expirySecond = Math.floorDiv(expiry.getTime(), 1_000);
        long nowSecond = Instant.now().getEpochSecond();
        long issuedSecond = Math.min(nowSecond - 5, expirySecond - 1);
        Map<String, Object> claims = validAccessClaims();
        claims.put("iat", Date.from(Instant.ofEpochSecond(issuedSecond)));
        claims.put("exp", Date.from(Instant.ofEpochSecond(expirySecond)));
        claims.putAll(overrides);
        return signedJjwtToken(keyPair.getPrivate(), claims);
    }

    private String generateBootstrapToken() {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "123");
        claims.put("iss", "rutcampustrack-auth");
        claims.put("aud", "rutcampustrack");
        claims.put("iat", Date.from(Instant.ofEpochSecond(now - 5)));
        claims.put("exp", Date.from(Instant.ofEpochSecond(now + 60)));
        claims.put("token_use", "bootstrap");
        claims.put("sid", SESSION_ID);
        claims.put("sv", "7");
        claims.put("rv", "3");
        return signedJjwtToken(keyPair.getPrivate(), claims);
    }

    private static String signedJjwtToken(PrivateKey privateKey, Map<String, Object> claims) {
        var builder = Jwts.builder();
        claims.forEach(builder::claim);
        return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    }

    private static String signedRawToken(Map<String, Object> claims) {
        try {
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            Map<String, Object> payloadClaims = new LinkedHashMap<>(claims);
            for (String name : new String[]{"iat", "exp"}) {
                Object value = payloadClaims.get(name);
                if (value instanceof Date date) {
                    payloadClaims.put(name, date.getTime() / 1_000L);
                }
            }
            String payload = encoder.encodeToString(new ObjectMapper().writeValueAsBytes(payloadClaims));
            String signingInput = header + "." + payload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + encoder.encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign raw JWT test fixture", e);
        }
    }

    private void assertUnauthorized(String token) {
        assertUnauthorized("invalid token", token);
    }

    private void assertUnauthorized(String reason, String token) {
        var exchange = exchange(MockServerHttpRequest.get("/api/academic/groups")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build());
        var chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).as(reason)
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private static GatewayFilterChain acceptingChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private void assertLogoutPasses(MockServerHttpRequest request) {
        var exchange = exchange(request);
        var chain = acceptingChain();

        filter.filter(exchange, chain).block();

        verify(chain).filter(any());
        assertThat((String) exchange.getAttribute(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE))
                .isNull();
    }

    private static Date futureExpiry() {
        return Date.from(Instant.now().plusSeconds(60));
    }

    private static Date expiredExpiry() {
        return Date.from(Instant.now().minusSeconds(60));
    }
}
