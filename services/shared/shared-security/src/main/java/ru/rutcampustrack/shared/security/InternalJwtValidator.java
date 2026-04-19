package ru.rutcampustrack.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

/**
 * Validates Internal JWT signed by auth-service. Rejects tokens with
 * wrong signature, issuer, audience, or expired.
 *
 * Thread-safe after construction — {@link PublicKeyProvider} holds
 * the key in {@link java.util.concurrent.atomic.AtomicReference}.
 */
public class InternalJwtValidator {

    private final PublicKeyProvider publicKeyProvider;
    private final InternalJwtProperties properties;

    public InternalJwtValidator(PublicKeyProvider publicKeyProvider, InternalJwtProperties properties) {
        this.publicKeyProvider = publicKeyProvider;
        this.properties = properties;
    }

    /**
     * @throws InternalJwtException if signature / issuer / audience / expiration invalid.
     */
    public InternalJwtClaims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new InternalJwtException("Token is missing");
        }
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(publicKeyProvider.getPublicKey())
                    .requireIssuer(properties.expectedIssuer())
                    .requireAudience(properties.expectedAudience())
                    .clockSkewSeconds(properties.clockSkewSeconds())
                    .build()
                    .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            Long userId = parseLong(claims.getSubject(), "sub");
            String role = claims.get("role", String.class);
            if (role == null || role.isBlank()) {
                throw new InternalJwtException("Missing 'role' claim");
            }
            Long groupId = null;
            Object groupClaim = claims.get("group_id");
            if (groupClaim != null) {
                groupId = Long.valueOf(groupClaim.toString());
            }
            Boolean isHeadman = claims.get("is_headman", Boolean.class);

            return new InternalJwtClaims(userId, role, groupId, Boolean.TRUE.equals(isHeadman));

        } catch (ExpiredJwtException e) {
            throw new InternalJwtException("Token expired", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InternalJwtException("Invalid token: " + e.getMessage(), e);
        }
    }

    private static Long parseLong(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new InternalJwtException("Missing '" + claim + "' claim");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new InternalJwtException("Claim '" + claim + "' is not numeric: " + value, e);
        }
    }
}
