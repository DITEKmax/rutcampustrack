package ru.rutcampustrack.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.jsonwebtoken.Jwts;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Internal-Token"))
                .isEqualTo(response.internalToken());
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-Login")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Group-Id"));
        assertThat(forwarded.get().getRequest().getHeaders().keySet())
                .noneMatch(name -> name.equalsIgnoreCase("X-Is-Headman"));
        assertThat(
                        (String)
                                forwarded
                                        .get()
                                        .getAttribute(
                                                JwtAuthenticationFilter
                                                        .AUTHENTICATED_USER_ID_ATTRIBUTE))
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
    void headmanAdmission_withoutGroupId_returns503WithoutDownstreamCall() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Instant tokenExpiry = accessExpiry.minusSeconds(60);
        Map<String, Object> claims = validClaims(tokenExpiry);
        claims.put("role", "HEADMAN");
        claims.put("is_headman", true);
        String token = signedRawToken(claims);
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(headmanResponse(token, tokenExpiry)));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
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

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void missingAccessExpectations_returns503WithoutAuthorityCall() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(
                        MockServerHttpRequest.get("/api/academic/users").build());
        exchange.getAttributes()
                .put(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(client);
    }

    @Test
    void authorityDenial_returnsTypedPublicResponseWithoutDownstreamCall() {
        when(client.admit(ACCESS_TOKEN))
                .thenReturn(
                        Mono.error(
                                new InternalAdmissionDeniedException(
                                        HttpStatus.FORBIDDEN, "WRONG_ROLE")));
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

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void fractionalInternalExpiration_returns503WithoutDownstreamCall() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Instant tokenExpiry = accessExpiry.minusSeconds(60);
        Map<String, Object> claims = validClaims(tokenExpiry);
        claims.put(
                "exp",
                BigDecimal.valueOf(tokenExpiry.getEpochSecond()).add(BigDecimal.valueOf(0.5)));
        String token = signedRawToken(claims);
        AuthAdmissionResponse response = response(token, tokenExpiry);
        when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
        MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
        GatewayFilterChain chain = ex -> Mono.error(new AssertionError("chain should not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
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
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/login").build());
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

    @Test
    void diagnostics_distinguishSignedTimeAndNonTimeFailuresWithoutLeakingIdentity() {
        Instant expiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("iss", "private-invalid-issuer");
        changes.put("aud", "private-invalid-audience");
        changes.put("token_use", "private-invalid-use");
        changes.put("iat", expiry.getEpochSecond() - 30);
        changes.put("sub", "938462751");
        changes.put("sid", "00000000-0000-0000-0000-000000000099");
        changes.put("sv", "938462752");
        changes.put("rv", "938462753");
        changes.put("role", "TEACHER");
        changes.put("status", "EXPELLED");
        changes.put("group_id", "938462754");
        changes.put("is_headman", true);
        changes.put("readOnly", true);
        Map<String, String> checks = Map.ofEntries(
                Map.entry("iss", "JWT_ISSUER"), Map.entry("aud", "JWT_AUDIENCE"),
                Map.entry("token_use", "JWT_USE"), Map.entry("iat", "JWT_IAT_FUTURE"),
                Map.entry("sub", "JWT_USER"), Map.entry("sid", "JWT_SESSION"),
                Map.entry("sv", "JWT_SESSION_VERSION"), Map.entry("rv", "JWT_ROLES_VERSION"),
                Map.entry("role", "JWT_ROLE"), Map.entry("status", "JWT_STATUS"),
                Map.entry("group_id", "JWT_GROUP"), Map.entry("is_headman", "JWT_HEADMAN_FLAG"),
                Map.entry("readOnly", "JWT_READONLY_FLAG"));
        for (var change : changes.entrySet()) {
            Map<String, Object> claims = validClaims(expiry);
            claims.put("iat", Instant.now().getEpochSecond() - 10);
            claims.put(change.getKey(), change.getValue());
            String token = signedRawToken(claims);
            assertDiagnostic(response(token, expiry), expiry.plusSeconds(60), checks.get(change.getKey()), "NONE");
        }
        Map<String, Object> claims = validClaims(expiry);
        claims.put("iat", expiry.getEpochSecond());
        assertDiagnostic(response(signedRawToken(claims), expiry), expiry.plusSeconds(60), "JWT_TIME_INTERVAL", "NONE");
        claims = validClaims(expiry);
        claims.put("iat", new BigDecimal(Instant.now().getEpochSecond() - 10 + ".5"));
        assertDiagnostic(response(signedRawToken(claims), expiry), expiry.plusSeconds(60), "JWT_IAT_WIRE", "NONE");
        claims = validClaims(expiry);
        claims.put("iat", Instant.now().getEpochSecond() - 10);
        claims.put("exp", new BigDecimal(expiry.getEpochSecond() + ".5"));
        assertDiagnostic(response(signedRawToken(claims), expiry), expiry.plusSeconds(60), "JWT_EXP_WIRE", "NONE");
        claims = validClaims(expiry);
        claims.put("iat", Instant.now().getEpochSecond() - 10);
        String token = signedRawToken(claims);
        assertDiagnostic(response(token, expiry.plusSeconds(1)), expiry.plusSeconds(60), "JWT_RESPONSE_EXPIRY", "NONE");
        assertDiagnostic(response(token, expiry), expiry.minusSeconds(1), "JWT_ACCESS_EXPIRY", "NONE");
        assertDiagnostic(response("invalid.private-token", expiry), expiry.plusSeconds(60), "JWT_PARSE", "MALFORMED");
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertDiagnostic(response(String.join(".", parts), expiry), expiry.plusSeconds(60), "JWT_PARSE", "SIGNATURE");
        Instant past = Instant.now().minusSeconds(120);
        assertDiagnostic(response(signedRawToken(validClaims(past)), past), expiry.plusSeconds(60), "JWT_PARSE", "EXPIRED");
    }

    @Test
    void diagnostics_markUnexpectedFailuresWithoutExceptionText() {
        when(publicKeyConfig.getPublicKey()).thenThrow(new UnsupportedOperationException("private-exception-message"));
        List<ILoggingEvent> events = rejectAndCapture(validAdmission(), Instant.now().plusSeconds(180));
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getFormattedMessage()).contains("cause=UNEXPECTED", "check=JWT_KEY")
                .doesNotContain("private-exception-message");
        assertThat(events.getFirst().getThrowableProxy()).isNull();
    }

    private void assertDiagnostic(AuthAdmissionResponse response, Instant accessExpiry, String check, String parser) {
        List<ILoggingEvent> events = rejectAndCapture(response, accessExpiry);
        assertThat(events).hasSize(1);
        ILoggingEvent event = events.getFirst();
        assertThat(event.getFormattedMessage()).contains("cause=IDENTITY_REJECTED", "check=" + check + " ", "parser=" + parser + " ")
                .doesNotContain(response.internalToken(), ACCESS_TOKEN, SESSION_ID, "private-", "93846275", "sub=", "sid=");
        assertThat(event.getThrowableProxy()).isNull();
    }

    private List<ILoggingEvent> rejectAndCapture(AuthAdmissionResponse response, Instant accessExpiry) {
        Logger logger = (Logger) LoggerFactory.getLogger(InternalJwtIssuerFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(client.admit(ACCESS_TOKEN)).thenReturn(Mono.just(response));
            MockServerWebExchange exchange = exchangeWithExpectations(accessExpiry);
            GatewayFilterChain chain = mock(GatewayFilterChain.class);
            StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            verifyNoInteractions(chain);
            assertThat(exchange.getResponse().getBodyAsString().block()).doesNotContain("check=", "parser=", response.internalToken());
            return List.copyOf(appender.list);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private AuthAdmissionResponse validAdmission() {
        Instant tokenExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 60);
        return response(signedJjwtToken(tokenExpiry), tokenExpiry);
    }

    private AuthAdmissionResponse response(String internalToken, Instant expiresAt) {
        return new AuthAdmissionResponse(
                internalToken,
                expiresAt,
                SESSION_ID,
                "42",
                "7",
                "3",
                "STUDENT",
                "ACTIVE",
                null,
                false,
                false);
    }

    private AuthAdmissionResponse headmanResponse(String internalToken, Instant expiresAt) {
        return new AuthAdmissionResponse(
                internalToken,
                expiresAt,
                SESSION_ID,
                "42",
                "7",
                "3",
                "HEADMAN",
                "ACTIVE",
                null,
                true,
                false);
    }

    private MockServerWebExchange exchangeWithExpectations() {
        Instant accessExpiry = Instant.ofEpochSecond(Instant.now().getEpochSecond() + 120);
        return exchangeWithExpectations(accessExpiry);
    }

    private MockServerWebExchange exchangeWithExpectations(Instant accessExpiry) {
        MockServerHttpRequest request =
                MockServerHttpRequest.get("/api/academic/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                        .header("X-User-Id", "666")
                        .header("X-User-Role", "ADMIN")
                        .header("X-Login", "forged")
                        .header("x-GrOuP-Id", "666")
                        .header("X-iS-hEaDmAn", "true")
                        .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes()
                .put(JwtAuthenticationFilter.ORIGINAL_ACCESS_TOKEN_ATTRIBUTE, ACCESS_TOKEN);
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_USER_ID_ATTRIBUTE, "42");
        exchange.getAttributes()
                .put(JwtAuthenticationFilter.EXPECTED_SESSION_ID_ATTRIBUTE, SESSION_ID);
        exchange.getAttributes()
                .put(JwtAuthenticationFilter.EXPECTED_SESSION_VERSION_ATTRIBUTE, "7");
        exchange.getAttributes().put(JwtAuthenticationFilter.EXPECTED_ROLES_VERSION_ATTRIBUTE, "3");
        exchange.getAttributes()
                .put(JwtAuthenticationFilter.ORIGINAL_ACCESS_EXPIRY_ATTRIBUTE, accessExpiry);
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
                .audience()
                .add("rutcampustrack-internal")
                .and()
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
            String header =
                    encoder.encodeToString(
                            "{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
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
