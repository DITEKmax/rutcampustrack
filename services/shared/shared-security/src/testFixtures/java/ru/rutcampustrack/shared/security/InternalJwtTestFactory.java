package ru.rutcampustrack.shared.security;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Helper for generating valid / invalid Internal JWT tokens in tests.
 * Each factory instance owns its own RSA keypair — call {@link #publicKey()}
 * to wire into a {@link PublicKeyProvider} stub.
 */
public final class InternalJwtTestFactory {

    public static final String ISSUER = "rutcampustrack-auth";
    public static final String AUDIENCE = "rutcampustrack-internal";
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final KeyPair keyPair;

    public InternalJwtTestFactory() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            this.keyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA keypair for tests", e);
        }
    }

    public java.security.PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public String validToken(Long userId, String role, Long groupId, boolean isHeadman) {
        return buildToken(userId, role, groupId, isHeadman, DEFAULT_TTL, ISSUER, AUDIENCE);
    }

    public String expiredToken(Long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .claim("role", role)
                .issuedAt(Date.from(now.minus(Duration.ofMinutes(10))))
                .expiration(Date.from(now.minus(Duration.ofMinutes(5))))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    public String invalidSignature(Long userId, String role) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair wrong = gen.generateKeyPair();
            return Jwts.builder()
                    .subject(userId.toString())
                    .issuer(ISSUER)
                    .audience().add(AUDIENCE).and()
                    .claim("role", role)
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plus(DEFAULT_TTL)))
                    .signWith(wrong.getPrivate(), Jwts.SIG.RS256)
                    .compact();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String wrongIssuer(Long userId, String role) {
        return buildToken(userId, role, null, false, DEFAULT_TTL, "evil-issuer", AUDIENCE);
    }

    public String wrongAudience(Long userId, String role) {
        return buildToken(userId, role, null, false, DEFAULT_TTL, ISSUER, "some-other-audience");
    }

    public String missingRole(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(DEFAULT_TTL)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String buildToken(Long userId, String role, Long groupId, boolean isHeadman,
                              Duration ttl, String issuer, String audience) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .claim("is_headman", isHeadman)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)));
        if (groupId != null) {
            builder.claim("group_id", groupId);
        }
        return builder.signWith(keyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
