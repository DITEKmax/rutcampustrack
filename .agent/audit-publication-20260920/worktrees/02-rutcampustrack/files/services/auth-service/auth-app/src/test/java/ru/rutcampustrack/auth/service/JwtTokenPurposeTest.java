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
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = jwtService.generateInternalToken(
                snapshot(), issuedAt, issuedAt.plusSeconds(60));

        Claims claims = Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .requireIssuer(ISSUER)
                .requireAudience(JwtService.INTERNAL_JWT_AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.get(JwtService.TOKEN_USE_CLAIM, String.class))
                .isEqualTo(JwtService.TOKEN_USE_INTERNAL);
        assertThat(claims.get("sid", String.class))
                .isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(claims.get("sv", String.class)).isEqualTo("2");
        assertThat(claims.get("rv", String.class)).isEqualTo("3");
        assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(claims.get("readOnly", Boolean.class)).isFalse();
        assertThatThrownBy(() -> jwtService.parseAccessToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sessionAccessTokenCarriesFrozenWireAndLegacyAccessIsNotAdmitted() {
        Instant issuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String token = jwtService.generateSessionAccessToken(
                snapshot(), issuedAt, issuedAt.plusSeconds(60));

        Claims claims = jwtService.parseSessionAccessToken(token).getPayload();

        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getAudience()).containsExactly(AUDIENCE);
        assertThat(claims.get(JwtService.TOKEN_USE_CLAIM, String.class))
                .isEqualTo(JwtService.TOKEN_USE_ACCESS);
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(claims.get("sid", String.class))
                .isEqualTo("33333333-3333-4333-8333-333333333333");
        assertThat(claims.get("sv", String.class)).isEqualTo("2");
        assertThat(claims.get("rv", String.class)).isEqualTo("3");
        assertThat(claims.get("role", String.class)).isEqualTo("STUDENT");
        assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(claims.get("group_id", String.class)).isEqualTo("987");
        assertThat(claims.get("is_headman", Boolean.class)).isFalse();
        assertThat(claims.get("readOnly", Boolean.class)).isFalse();
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(issuedAt));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(issuedAt.plusSeconds(60)));
        String legacy = jwtService.generateAccessToken(user);
        assertThatThrownBy(() -> jwtService.parseSessionAccessToken(legacy))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void sessionAccessParserRejectsUnknownRolesStatusesAndInvalidDecimalStrings() {
        List<RawOverride> invalidClaims = List.of(
                new RawOverride("role", "\"UNKNOWN\""),
                new RawOverride("status", "\"UNKNOWN\""),
                new RawOverride("sub", "\"0\""),
                new RawOverride("sub", "\"-1\""),
                new RawOverride("sub", "\"01\""),
                new RawOverride("sub", "\"9223372036854775808\""),
                new RawOverride("sv", "\"0\""),
                new RawOverride("sv", "\"-1\""),
                new RawOverride("sv", "\"9223372036854775808\""),
                new RawOverride("rv", "\"0\""),
                new RawOverride("rv", "\"-1\""),
                new RawOverride("rv", "\"9223372036854775808\""),
                new RawOverride("group_id", "\"0\""),
                new RawOverride("group_id", "\"-1\""),
                new RawOverride("group_id", "\"9223372036854775808\"")
        );

        for (RawOverride invalidClaim : invalidClaims) {
            assertSessionRejected(rawSessionToken(invalidClaim.name(), invalidClaim.value()));
        }
    }

    @Test
    void sessionAccessParserRejectsMissingRequiredClaims() {
        for (String requiredClaim : List.of(
                "iss", "aud", "token_use", "sub", "sid", "sv", "rv", "role",
                "status", "is_headman", "readOnly", "iat", "exp")) {
            assertSessionRejected(rawSessionTokenWithout(requiredClaim));
        }
    }

    @Test
    void sessionAccessParserAcceptsSignedTokenWithoutOptionalGroupId() {
        Claims claims = jwtService.parseSessionAccessToken(rawSessionTokenWithout("group_id")).getPayload();

        assertThat(claims.get("group_id")).isNull();
    }

    @Test
    void sessionAccessParserRejectsSemanticIdentityMismatches() {
        List<RawOverride> invalidClaims = List.of(
                new RawOverride("status", "\"SUSPENDED\""),
                new RawOverride("is_headman", "true"),
                new RawOverride("readOnly", "true"),
                new RawOverride("status", "\"EXPELLED\""));

        for (RawOverride invalidClaim : invalidClaims) {
            assertSessionRejected(rawSessionToken(invalidClaim.name(), invalidClaim.value()));
        }
    }

    @Test
    void sessionAccessParserRejectsFutureAndNonNumberTimes() {
        long epoch = Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond();

        assertSessionRejected(rawSessionToken(
                "iat", Long.toString(epoch + 120), "exp", Long.toString(epoch + 180)));
        assertSessionRejected(rawSessionToken("iat", "\"not-a-number\""));
        assertSessionRejected(rawSessionToken("exp", "\"not-a-number\""));
    }

    @Test
    void sessionAccessParserRejectsRawTypeCoercionAndFractionalOrExponentTimes() {
        long epoch = Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        assertSessionRejected(rawSessionToken("sv", "1"));
        assertSessionRejected(rawSessionToken("readOnly", "\"false\""));
        assertSessionRejected(rawSessionToken("iat", "\"" + epoch + "\""));
        assertSessionRejected(rawSessionToken("exp", "\"" + (epoch + 60) + "\""));
        assertSessionRejected(rawSessionToken("iat", epoch + ".5"));
        assertSessionRejected(rawSessionToken("exp", (epoch + 60) + ".5"));
        assertSessionRejected(rawSessionToken("iat", epoch + "e0"));
        assertSessionRejected(rawSessionToken("exp", (epoch + 60) + "e0"));
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

    private void assertSessionRejected(String token) {
        assertThatThrownBy(() -> jwtService.parseSessionAccessToken(token))
                .isInstanceOf(RuntimeException.class);
    }

    private String rawSessionToken(String... overrides) {
        return rawSessionTokenInternal(null, overrides);
    }

    private String rawSessionTokenWithout(String omitted) {
        return rawSessionTokenInternal(omitted);
    }

    private String rawSessionTokenInternal(String omitted, String... overrides) {
        long epoch = Instant.now().truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("iss", "\"" + ISSUER + "\"");
        values.put("aud", "[\"" + AUDIENCE + "\"]");
        values.put("token_use", "\"access\"");
        values.put("sub", "\"" + USER_ID + "\"");
        values.put("sid", "\"33333333-3333-4333-8333-333333333333\"");
        values.put("sv", "\"2\"");
        values.put("rv", "\"3\"");
        values.put("role", "\"STUDENT\"");
        values.put("status", "\"ACTIVE\"");
        values.put("group_id", "\"987\"");
        values.put("is_headman", "false");
        values.put("readOnly", "false");
        values.put("iat", Long.toString(epoch));
        values.put("exp", Long.toString(epoch + 60));
        for (int index = 0; index < overrides.length; index += 2) {
            values.put(overrides[index], overrides[index + 1]);
        }
        if (omitted != null) {
            values.remove(omitted);
        }
        StringBuilder payload = new StringBuilder("{");
        values.forEach((name, value) -> {
            if (payload.length() > 1) {
                payload.append(',');
            }
            payload.append('"').append(name).append("\":").append(value);
        });
        return signRawCompact(payload.append('}').toString());
    }

    private String signRawCompact(String payload) {
        try {
            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
            String encodedPayload = base64Url(payload);
            String input = header + "." + encodedPayload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(keyPair.getPrivate());
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + base64Url(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
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

    private static SessionSnapshot snapshot() {
        Instant created = Instant.parse("2026-09-10T08:00:00Z");
        RoleGrant grant = new RoleGrant(
                1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, GROUP_ID,
                created, created);
        return new SessionSnapshot(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                USER_ID,
                2L,
                3L,
                grant,
                java.util.List.of(grant),
                created.plusSeconds(3600),
                created,
                created,
                null,
                null,
                AuthMethod.OTP,
                "test",
                "test"
        );
    }

    private record RawOverride(String name, String value) {
    }
}
