package ru.rutcampustrack.auth.config;

import io.jsonwebtoken.Jwts;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.UserRole;
import ru.rutcampustrack.auth.service.JwtService;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;

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
        filter = new JwtAuthenticationFilter(jwtService);

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
        runWithBearer(jwtService.generateAccessToken(user));

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
        String internal = jwtService.generateInternalToken(
                USER_ID, UserRole.STUDENT.name(), GROUP_ID, false, 60L);
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

    private void runWithBearer(String token) throws ServletException, java.io.IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
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
