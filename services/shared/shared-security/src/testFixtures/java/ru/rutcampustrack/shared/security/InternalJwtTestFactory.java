package ru.rutcampustrack.shared.security;

import io.jsonwebtoken.Jwts;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Test-only signer for the complete internal JWT wire. */
public final class InternalJwtTestFactory {

    public static final String ISSUER = "rutcampustrack-auth";
    public static final String AUDIENCE = "rutcampustrack-internal";
    public static final String KEY_ID = "test-kid";
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final KeyPair keyPair;

    public InternalJwtTestFactory() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            this.keyPair = gen.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate RSA keypair for tests", exception);
        }
    }

    public java.security.PublicKey publicKey() {
        return keyPair.getPublic();
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public String validToken(
            long userId,
            UUID sessionId,
            long sessionVersion,
            long rolesVersion,
            String role,
            String status,
            Long groupId,
            boolean isHeadman,
            boolean readOnly
    ) {
        Instant issuedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        return buildToken(
                userId,
                sessionId,
                sessionVersion,
                rolesVersion,
                role,
                status,
                groupId,
                isHeadman,
                readOnly,
                issuedAt,
                issuedAt.plus(DEFAULT_TTL),
                ISSUER,
                AUDIENCE,
                "internal",
                keyPair
        );
    }

    public String buildToken(
            long userId,
            UUID sessionId,
            long sessionVersion,
            long rolesVersion,
            String role,
            String status,
            Long groupId,
            boolean isHeadman,
            boolean readOnly,
            Instant issuedAt,
            Instant expiration,
            String issuer,
            String audience,
            String tokenUse,
            KeyPair signer
    ) {
        var builder = Jwts.builder()
                .header().keyId(KEY_ID).and()
                .subject(Long.toString(userId))
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("token_use", tokenUse)
                .claim("sid", sessionId.toString())
                .claim("sv", Long.toString(sessionVersion))
                .claim("rv", Long.toString(rolesVersion))
                .claim("role", role)
                .claim("status", status)
                .claim("is_headman", isHeadman)
                .claim("readOnly", readOnly)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiration));
        if (groupId != null) {
            builder.claim("group_id", Long.toString(groupId));
        }
        return builder.signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
