package ru.rutcampustrack.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.UserRole;

import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenPurposeTest {

    private static final long USER_ID = 424242L;
    private static final long GROUP_ID = 987L;
    private static final String ISSUER = "rutcampustrack-auth";
    private static final String AUDIENCE = "rutcampustrack";

    private JwtService jwtService;
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

        user = mock(User.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getRole()).thenReturn(UserRole.STUDENT);
        when(user.getGroupId()).thenReturn(GROUP_ID);
        when(user.isHeadman()).thenReturn(false);
    }

    @Test
    void generatedAccessTokenIsStrictlyAcceptedAndParseTokenIsAccessAlias() {
        String token = jwtService.generateAccessToken(user);

        Jws<Claims> parsed = jwtService.parseAccessToken(token);

        assertThat(parsed.getPayload().getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(parsed.getPayload().get("token_use", String.class))
                .isEqualTo(JwtService.TOKEN_USE_ACCESS);
        assertThat(jwtService.parseToken(token).getPayload().getSubject())
                .isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    void generatedRefreshTokenIsAcceptedOnlyByRefreshParserAndExtractors() {
        String token = jwtService.generateRefreshToken(user);

        Jws<Claims> parsed = jwtService.parseRefreshToken(token);

        assertThat(parsed.getPayload().get("token_use", String.class))
                .isEqualTo(JwtService.TOKEN_USE_REFRESH);
        assertThat(parsed.getPayload().getId()).isNotBlank();
        assertThat(jwtService.extractUserId(token)).isEqualTo(USER_ID);
        assertThat(jwtService.extractJti(token)).isEqualTo(parsed.getPayload().getId());
        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void accessTokenCannotBeUsedByRefreshParser() {
        String token = jwtService.generateAccessToken(user);

        assertThatThrownBy(() -> jwtService.parseRefreshToken(token))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwtService.extractJti(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void internalTokenCarriesInternalPurposeAndCannotBeParsedAsAccess() {
        String token = jwtService.generateInternalToken(
                USER_ID, UserRole.STUDENT.name(), GROUP_ID, false, 60L);

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .requireIssuer(ISSUER)
                .requireAudience(JwtService.INTERNAL_JWT_AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get(JwtService.TOKEN_USE_CLAIM, String.class))
                .isEqualTo(JwtService.TOKEN_USE_INTERNAL);
        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void accessParserRejectsMissingOrWrongPurpose() {
        assertAccessRejected(signedToken(null, String.valueOf(USER_ID), future(), "STUDENT", null));
        assertAccessRejected(signedToken("refresh", String.valueOf(USER_ID), future(), "STUDENT", null));
    }

    @Test
    void accessParserRejectsMalformedRequiredClaims() {
        assertAccessRejected(signedToken("access", null, future(), "STUDENT", null));
        assertAccessRejected(signedToken("access", "not-a-number", future(), "STUDENT", null));
        assertAccessRejected(signedToken("access", "0", future(), "STUDENT", null));
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), null, "STUDENT", null));
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), expired(), "STUDENT", null));
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), future(), null, null));
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), future(), "UNKNOWN", null));
    }

    @Test
    void parserStillRejectsWrongIssuerAudienceAndSignature() throws Exception {
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), future(), "STUDENT", null,
                "wrong-issuer", AUDIENCE, keyPair.getPrivate()));
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), future(), "STUDENT", null,
                ISSUER, "wrong-audience", keyPair.getPrivate()));

        KeyPair otherKeyPair = generateKeyPair();
        assertAccessRejected(signedToken("access", String.valueOf(USER_ID), future(), "STUDENT", null,
                ISSUER, AUDIENCE, otherKeyPair.getPrivate()));
    }

    @Test
    void refreshParserRejectsMissingPurposeSubjectExpirationOrJti() {
        assertRefreshRejected(signedToken(null, String.valueOf(USER_ID), future(), null, "jti"));
        assertRefreshRejected(signedToken("access", String.valueOf(USER_ID), future(), null, "jti"));
        assertRefreshRejected(signedToken("refresh", "-1", future(), null, "jti"));
        assertRefreshRejected(signedToken("refresh", String.valueOf(USER_ID), null, null, "jti"));
        assertRefreshRejected(signedToken("refresh", String.valueOf(USER_ID), future(), null, null));
        assertRefreshRejected(signedToken("refresh", String.valueOf(USER_ID), future(), null, "   "));
    }

    private void assertAccessRejected(String token) {
        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    private void assertRefreshRejected(String token) {
        assertThatThrownBy(() -> jwtService.parseRefreshToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    private String signedToken(String purpose,
                               String subject,
                               Date expiration,
                               String role,
                               String jti) {
        return signedToken(purpose, subject, expiration, role, jti, ISSUER, AUDIENCE, keyPair.getPrivate());
    }

    private String signedToken(String purpose,
                               String subject,
                               Date expiration,
                               String role,
                               String jti,
                               String issuer,
                               String audience,
                               PrivateKey signingKey) {
        JwtBuilder builder = Jwts.builder()
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date());
        if (purpose != null) {
            builder.claim(JwtService.TOKEN_USE_CLAIM, purpose);
        }
        if (expiration != null) {
            builder.expiration(expiration);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        if (jti != null) {
            builder.id(jti);
        }
        return builder.signWith(signingKey, Jwts.SIG.RS256).compact();
    }

    private static Date future() {
        return new Date(System.currentTimeMillis() + 60_000L);
    }

    private static Date expired() {
        return new Date(System.currentTimeMillis() - 1_000L);
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
