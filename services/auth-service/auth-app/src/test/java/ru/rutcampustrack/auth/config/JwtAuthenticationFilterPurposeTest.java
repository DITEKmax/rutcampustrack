package ru.rutcampustrack.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.UserRole;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterPurposeTest {

    private static final long USER_ID = 424242L;
    private static final long GROUP_ID = 987L;
    private static final String ISSUER = "rutcampustrack-auth";
    private static final String AUDIENCE = "rutcampustrack";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private ObjectMapper objectMapper;
    private KeyPair keyPair;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        JwtProperties properties = mock(JwtProperties.class);
        when(properties.accessTokenExpiration()).thenReturn(900L);
        when(properties.refreshTokenExpiration()).thenReturn(3600L);

        keyPair = generateKeyPair();
        jwtService = new JwtService(properties, mock(StringRedisTemplate.class));
        setField(jwtService, "privateKey", keyPair.getPrivate());
        setField(jwtService, "publicKey", keyPair.getPublic());
        setField(jwtService, "keyId", "test-kid");
        objectMapper = new ObjectMapper().findAndRegisterModules();
        filter = new JwtAuthenticationFilter(
                jwtService, null, java.time.Clock.systemUTC(), objectMapper);

        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        when(user.getGroupId()).thenReturn(GROUP_ID);
        when(user.isHeadman()).thenReturn(false);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validAccessTokenAuthenticatesWithKnownRole() throws ServletException, java.io.IOException {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        RoleGrant grant = new RoleGrant(1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE,
                GROUP_ID, now, now);
        SessionSnapshot snapshot = new SessionSnapshot(
                UUID.fromString("33333333-3333-4333-8333-333333333333"), USER_ID,
                1L, 1L, grant, List.of(grant), now.plusSeconds(300), now, now,
                null, null, AuthMethod.PASSWORD, null, null);
        runWithBearer(jwtService.generateSessionAccessToken(snapshot, now, now.plusSeconds(60)));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(String.valueOf(USER_ID));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_STUDENT");
    }

    @Test
    void refreshTokenIsNotAnAuthenticatedAccessRequest() throws ServletException, java.io.IOException {
        runWithBearer(jwtService.generateRefreshToken(user));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void internalTokenAndMalformedAccessClaimsAreRejected() throws Exception {
        String internal = signedInternalToken();
        runWithBearer(internal);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        runWithBearer(signedAccessToken(null, String.valueOf(USER_ID), "STUDENT"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        runWithBearer(signedAccessToken("access", "not-a-number", "STUDENT"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        runWithBearer(signedAccessToken("access", String.valueOf(USER_ID), "UNKNOWN"));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidBearerClearsPreexistingAuthentication() throws ServletException, java.io.IOException {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("stale", null));

        runWithBearer("malformed");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingBearerLeavesRequestUnauthenticated() throws ServletException, java.io.IOException {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void bootstrapBearerIsAcceptedOnlyOnTheExactSessionOperations() throws Exception {
        for (RequestSpec request : List.of(
                new RequestSpec("GET", "/auth/session"),
                new RequestSpec("PUT", "/auth/session/active-role"),
                new RequestSpec("GET", "/auth/sessions"),
                new RequestSpec("POST", "/auth/logout"),
                new RequestSpec("POST", "/auth/logout-all"),
                new RequestSpec("DELETE", "/auth/sessions/55555555-5555-4555-8555-555555555555"),
                new RequestSpec("DELETE", "/auth/sessions/not-a-uuid"))) {
            FilterRun result = runBootstrap(request);

            assertThat(result.status()).as(request.toString()).isEqualTo(200);
            assertThat(result.chainCalled()).as(request.toString()).isTrue();
            assertThat(result.authentication()).as(request.toString()).isNotNull();
            assertThat(result.authentication().getPrincipal()).isInstanceOf(
                    ru.rutcampustrack.auth.security.SessionPrincipal.class);
        }
    }

    @Test
    void bootstrapBearerIsDeniedForHistoryPasswordAndLookalikeRoutes() throws Exception {
        for (RequestSpec request : List.of(
                new RequestSpec("GET", "/auth/account-history"),
                new RequestSpec("POST", "/auth/change-password"),
                new RequestSpec("GET", "/auth/session/child"),
                new RequestSpec("GET", "/auth/sessions/other"),
                new RequestSpec("GET", "/auth/session/"),
                new RequestSpec("POST", "/auth/session"),
                new RequestSpec("PUT", "/auth/logout"),
                new RequestSpec("POST", "/auth/logout-all/"),
                new RequestSpec("POST", "/auth/sessions/55555555-5555-4555-8555-555555555555"),
                new RequestSpec("GET", "/auth/sessions/55555555-5555-4555-8555-555555555555"),
                new RequestSpec("DELETE", "/auth/sessions"),
                new RequestSpec("DELETE", "/auth/sessions/"),
                new RequestSpec("DELETE", "/auth/sessions/55555555-5555-4555-8555-555555555555/child"),
                new RequestSpec("DELETE", "/auth/sessions/55555555-5555-4555-8555-555555555555/"))) {
            FilterRun result = runBootstrap(request);

            assertThat(result.status()).as(request.toString()).isEqualTo(403);
            assertThat(result.chainCalled()).as(request.toString()).isFalse();
            assertThat(result.authentication()).as(request.toString()).isNull();
            assertThat(result.body()).as(request.toString())
                    .contains("BOOTSTRAP_SCOPE_DENIED");
        }
    }

    @Test
    void cookieLogoutIgnoresExpiredOrStaleIncidentalBearer() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        SessionStatePort authority = mock(SessionStatePort.class);

        FilterRun expired = runToken(
                new RequestSpec("POST", "/auth/logout"),
                jwtService.generateSessionAccessToken(
                        selectedSnapshot(now), now.minusSeconds(120), now.minusSeconds(60)),
                authority, now, "rct_refresh=valid-refresh-cookie");
        assertThat(expired.status()).isEqualTo(200);
        assertThat(expired.chainCalled()).isTrue();
        assertThat(expired.authentication()).isNull();

        when(authority.snapshot(org.mockito.ArgumentMatchers.any(SessionStatePort.SnapshotCommand.class)))
                .thenReturn(SessionStatePort.SnapshotResult.failure(
                        SessionStatePort.FailureCode.SESSION_STATE_STALE));
        FilterRun stale = runToken(
                new RequestSpec("POST", "/auth/logout"),
                jwtService.generateSessionAccessToken(
                        selectedSnapshot(now), now, now.plusSeconds(60)),
                authority, now, "rct_refresh=valid-refresh-cookie");
        assertThat(stale.status()).isEqualTo(200);
        assertThat(stale.chainCalled()).isTrue();
        assertThat(stale.authentication()).isNull();
    }

    @Test
    void cookieLogoutDoesNotTurnAuthorityFailureIntoCookieSuccess() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        SessionStatePort authority = mock(SessionStatePort.class);
        when(authority.snapshot(org.mockito.ArgumentMatchers.any(SessionStatePort.SnapshotCommand.class)))
                .thenReturn(SessionStatePort.SnapshotResult.failure(
                        SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE));

        FilterRun result = runToken(
                new RequestSpec("POST", "/auth/logout"),
                jwtService.generateSessionAccessToken(
                        selectedSnapshot(now), now, now.plusSeconds(60)),
                authority, now, "rct_refresh=valid-refresh-cookie");

        assertThat(result.status()).isEqualTo(503);
        assertThat(result.chainCalled()).isFalse();
        assertThat(result.authentication()).isNull();
        assertThat(result.body()).contains("AUTHORITY_UNAVAILABLE");
        assertThat(result.setCookie()).isNull();
    }

    private void runWithBearer(String token) throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private FilterRun runBootstrap(RequestSpec requestSpec) throws Exception {
        SecurityContextHolder.clearContext();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        SessionSnapshot snapshot = bootstrapSnapshot(now);
        SessionStatePort authority = mock(SessionStatePort.class);
        when(authority.snapshot(org.mockito.ArgumentMatchers.any(SessionStatePort.SnapshotCommand.class)))
                .thenReturn(SessionStatePort.SnapshotResult.success(snapshot));
        String token = jwtService.generateBootstrapToken(snapshot, now, now.plusSeconds(60));
        return runToken(requestSpec, token, authority, now, null);
    }

    private FilterRun runToken(
            RequestSpec requestSpec,
            String token,
            SessionStatePort authority,
            Instant now,
            String cookieHeader
    ) throws Exception {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthenticationFilter(
                jwtService, authority, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC),
                objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(requestSpec.method());
        request.setRequestURI(requestSpec.path());
        request.addHeader("Authorization", "Bearer " + token);
        if (cookieHeader != null) {
            request.addHeader("Cookie", cookieHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (ignoredRequest, ignoredResponse) -> chainCalled[0] = true;
        filter.doFilter(request, response, chain);
        return new FilterRun(
                response.getStatus(), response.getContentAsString(), chainCalled[0],
                SecurityContextHolder.getContext().getAuthentication(),
                response.getHeader(HttpHeaders.SET_COOKIE));
    }

    private SessionSnapshot bootstrapSnapshot(Instant now) {
        RoleGrant grant = new RoleGrant(
                1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, GROUP_ID, now, now);
        return new SessionSnapshot(
                UUID.fromString("33333333-3333-4333-8333-333333333333"), USER_ID,
                1L, 1L, null, List.of(grant), now.plusSeconds(3600), now, now,
                null, null, AuthMethod.PASSWORD, null, null);
    }

    private SessionSnapshot selectedSnapshot(Instant now) {
        RoleGrant grant = new RoleGrant(
                1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, GROUP_ID, now, now);
        return new SessionSnapshot(
                UUID.fromString("33333333-3333-4333-8333-333333333333"), USER_ID,
                1L, 1L, grant, List.of(grant), now.plusSeconds(3600), now, now,
                null, null, AuthMethod.PASSWORD, null, null);
    }

    private record RequestSpec(String method, String path) {
    }

    private record FilterRun(
            int status,
            String body,
            boolean chainCalled,
            Authentication authentication,
            String setCookie
    ) {
    }

    private String signedAccessToken(String purpose, String subject, String role) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000L));
        if (purpose != null) {
            builder.claim(JwtService.TOKEN_USE_CLAIM, purpose);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        return builder.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String signedInternalToken() {
        Date issuedAt = new Date((System.currentTimeMillis() / 1000L) * 1000L);
        return Jwts.builder()
                .subject(String.valueOf(USER_ID))
                .issuer(ISSUER)
                .audience().add(JwtService.INTERNAL_JWT_AUDIENCE).and()
                .claim(JwtService.TOKEN_USE_CLAIM, JwtService.TOKEN_USE_INTERNAL)
                .claim("sid", "44444444-4444-4444-8444-444444444444")
                .claim("sv", "1")
                .claim("rv", "1")
                .claim("role", UserRole.STUDENT.name())
                .claim("status", "ACTIVE")
                .claim("is_headman", false)
                .claim("readOnly", false)
                .issuedAt(issuedAt)
                .expiration(new Date(issuedAt.getTime() + 60_000L))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
