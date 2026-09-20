package ru.rutcampustrack.shared.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
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

class InternalJwtValidatorTest {

    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String CANONICAL_HEADER = "{\"alg\":\"RS256\",\"kid\":\"test-kid\"}";

    private InternalJwtTestFactory factory;
    private InternalJwtValidator validator;

    @BeforeEach
    void setUp() {
        factory = new InternalJwtTestFactory();
        InternalJwtProperties props = new InternalJwtProperties(
                null, 0, 0, true, null, null, null);
        PublicKeyProvider keyProvider = new PublicKeyProvider(props) {
            @Override
            public java.security.PublicKey getPublicKey() {
                return factory.publicKey();
            }
        };
        validator = new InternalJwtValidator(keyProvider, props);
    }

    @Test
    void validToken_returnsCompleteFrozenClaims() {
        String token = factory.validToken(
                42L, SESSION_ID, 3L, 7L, "ADMIN", "ACTIVE", 9L, false, false);

        InternalJwtClaims claims = validator.validate(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.sessionId()).isEqualTo(SESSION_ID);
        assertThat(claims.sessionVersion()).isEqualTo(3L);
        assertThat(claims.rolesVersion()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo("ADMIN");
        assertThat(claims.status()).isEqualTo("ACTIVE");
        assertThat(claims.groupId()).isEqualTo(9L);
        assertThat(claims.isHeadman()).isFalse();
        assertThat(claims.readOnly()).isFalse();
    }

    @Test
    void terminalToken_isReadOnlyAndCanOmitGroup() {
        String token = factory.validToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "EXPELLED", null, false, true);

        InternalJwtClaims claims = validator.validate(token);

        assertThat(claims.groupId()).isNull();
        assertThat(claims.status()).isEqualTo("EXPELLED");
        assertThat(claims.readOnly()).isTrue();
    }

    @Test
    void expiredToken_throws() {
        Instant now = now();
        String token = factory.buildToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false,
                now.minusSeconds(600), now.minusSeconds(300),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                "internal", currentKeyPair());

        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void wrongSignatureIssuerAudienceAndPurpose_throw() {
        assertRejected(factory.buildToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false,
                now(), now().plusSeconds(60),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                "internal", newKeyPair()));
        assertRejected(factory.buildToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false,
                now(), now().plusSeconds(60),
                "evil", InternalJwtTestFactory.AUDIENCE, "internal", currentKeyPair()));
        assertRejected(factory.buildToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false,
                now(), now().plusSeconds(60),
                InternalJwtTestFactory.ISSUER, "extra", "internal", currentKeyPair()));
        assertRejected(factory.buildToken(
                1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false,
                now(), now().plusSeconds(60),
                InternalJwtTestFactory.ISSUER, InternalJwtTestFactory.AUDIENCE,
                "access", currentKeyPair()));
    }

    @Test
    void onlyRs256Tokens_areAccepted() {
        assertRejected(signedTokenWithAlgorithm(Jwts.SIG.RS384));
        assertRejected(signedTokenWithAlgorithm(Jwts.SIG.RS512));
        assertRejected(signedTokenWithAlgorithm(Jwts.SIG.PS256));
    }

    @Test
    void joseHeader_mustContainExactlyCanonicalAlgAndType() {
        assertRejected(rawTokenWithHeader("{\"kid\":\"test-kid\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS384\",\"kid\":\"test-kid\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":true,\"kid\":\"test-kid\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":true}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"foo\":\"unexpected\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"alg\":\"RS256\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"typ\":\"JWS\"}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"typ\":true}"));
        assertRejected(rawTokenWithHeader("{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"typ\":\"J\\u0057T\"}"));
    }

    @Test
    void joseHeader_mayIncludeCanonicalJwtType() {
        assertThat(validator.validate(rawTokenWithHeader(
                "{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"typ\":\"JWT\"}")))
                .isEqualTo(new InternalJwtClaims(
                        1L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", null, false, false));
    }

    @Test
    void duplicateJoseHeader_withValidSignature_isRejected() {
        String token = rawTokenWithHeader(
                "{\"alg\":\"RS256\",\"kid\":\"test-kid\",\"alg\":\"RS256\"}");

        assertSignatureIsValid(token);
        assertRejected(token);
    }

    @Test
    void duplicatePayload_withValidSignature_isRejected() {
        String token = signRawPayload(rawPayloadJson(null).replace(
                "\"role\":\"STUDENT\"", "\"role\":\"STUDENT\",\"role\":\"STUDENT\""));

        assertSignatureIsValid(token);
        assertRejected(token);
    }

    @Test
    void missingAndWrongTypeClaims_throw() {
        assertRejected(rawToken("sid", "42"));
        assertRejected(rawToken("sv", "1"));
        assertRejected(rawToken("rv", "\"0\""));
        assertRejected(rawToken("role", "\"UNKNOWN\""));
        assertRejected(rawToken("status", "\"UNKNOWN\""));
        assertRejected(rawToken("status", "\"SUSPENDED\""));
        assertRejected(rawToken("is_headman", "\"false\""));
        assertRejected(rawToken("readOnly", "\"false\""));
        assertRejected(rawToken("group_id", "7"));
        assertRejected(rawToken("aud", "[\"rutcampustrack-internal\",\"extra\"]"));
    }

    @Test
    void everyFrozenRequiredClaimMustBePresent() {
        for (String requiredClaim : List.of(
                "iss", "aud", "token_use", "sub", "sid", "sv", "rv", "role",
                "status", "is_headman", "readOnly", "iat", "exp")) {
            assertRejected(rawTokenWithout(requiredClaim));
        }
    }

    @Test
    void canonicalAndSemanticInvariants_areRequired() {
        assertRejected(rawToken("sid", "\"11111111-1111-4111-8111-11111111111A\""));
        assertRejected(rawToken("sub", "\"01\""));
        assertRejected(rawToken("sub", "\"9223372036854775808\""));
        assertRejected(rawToken("sv", "\"9223372036854775808\""));
        assertRejected(rawToken("rv", "\"-1\""));
        assertRejected(rawToken("group_id", "\"9223372036854775808\""));
        assertRejected(rawToken("is_headman", "true", "role", "\"STUDENT\""));
        assertRejected(rawToken("readOnly", "true", "status", "\"ACTIVE\""));
        assertRejected(rawToken("readOnly", "false", "status", "\"GRADUATED\""));
    }

    @Test
    void timeClaims_mustBeWholeSecondAndOrdered() {
        long epoch = now().getEpochSecond();
        assertRejected(rawToken("iat", "\"" + epoch + "\""));
        assertRejected(rawToken("exp", "\"" + (epoch + 60) + "\""));
        assertRejected(rawToken("iat", epoch + ".5"));
        assertRejected(rawToken("exp", (epoch + 60) + ".5"));
        assertRejected(rawToken("iat", epoch + "e0"));
        assertRejected(rawToken("exp", (epoch + 60) + "e0"));
        assertRejected(rawToken("iat", Long.toString(epoch + 60),
                "exp", Long.toString(epoch + 60)));
        assertRejected(rawToken("iat", Long.toString(epoch + 120),
                "exp", Long.toString(epoch + 180)));
        assertRejected(rawToken("iat", "\"not-a-number\""));
    }

    @Test
    void blankAndNullToken_throwWithoutBearerInMessage() {
        assertThatThrownBy(() -> validator.validate(""))
                .isInstanceOf(InternalJwtException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InternalJwtException.class);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> validator.validate(token))
                .isInstanceOf(InternalJwtException.class);
    }

    private String rawToken(String... overrides) {
        return rawTokenInternal(null, overrides);
    }

    private String rawTokenWithout(String omitted) {
        return rawTokenInternal(omitted, new String[0]);
    }

    private String rawTokenInternal(String omitted, String... overrides) {
        return signRawPayload(rawPayloadJson(omitted, overrides));
    }

    private String rawTokenWithHeader(String header) {
        return signRawToken(header, rawPayloadJson(null));
    }

    private String rawPayloadJson(String omitted, String... overrides) {
        long epoch = now().getEpochSecond();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("iss", "\"" + InternalJwtTestFactory.ISSUER + "\"");
        values.put("aud", "[\"" + InternalJwtTestFactory.AUDIENCE + "\"]");
        values.put("token_use", "\"internal\"");
        values.put("sub", "\"1\"");
        values.put("sid", "\"" + SESSION_ID + "\"");
        values.put("sv", "\"1\"");
        values.put("rv", "\"1\"");
        values.put("role", "\"STUDENT\"");
        values.put("status", "\"ACTIVE\"");
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
        return payload.append('}').toString();
    }

    private String signRawPayload(String payload) {
        return signRawToken(CANONICAL_HEADER, payload);
    }

    private String signRawToken(String header, String payload) {
        try {
            String encodedHeader = base64Url(header);
            String encodedPayload = base64Url(payload);
            String signingInput = encodedHeader + "." + encodedPayload;
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(currentKeyPair().getPrivate());
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64Url(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String signedTokenWithAlgorithm(SignatureAlgorithm algorithm) {
        Instant issuedAt = now();
        return Jwts.builder()
                .header().keyId(InternalJwtTestFactory.KEY_ID).and()
                .subject("1")
                .issuer(InternalJwtTestFactory.ISSUER)
                .audience().add(InternalJwtTestFactory.AUDIENCE).and()
                .claim("token_use", "internal")
                .claim("sid", SESSION_ID.toString())
                .claim("sv", "1")
                .claim("rv", "1")
                .claim("role", "STUDENT")
                .claim("status", "ACTIVE")
                .claim("is_headman", false)
                .claim("readOnly", false)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(60)))
                .signWith(currentKeyPair().getPrivate(), algorithm)
                .compact();
    }

    private void assertSignatureIsValid(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            assertThat(parts).hasSize(3);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(factory.publicKey());
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            assertThat(signature.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
        } catch (Exception exception) {
            throw new AssertionError("Fixture signature setup failed", exception);
        }
    }

    private static String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private KeyPair currentKeyPair() {
        return factory.keyPair();
    }

    private static KeyPair newKeyPair() {
        try {
            var generator = java.security.KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
