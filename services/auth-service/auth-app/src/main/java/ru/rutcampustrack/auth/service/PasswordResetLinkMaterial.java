package ru.rutcampustrack.auth.service;

import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.session.AuthSessionException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Pure trusted-link construction; the caller owns authorization and the ticket transaction. */
final class PasswordResetLinkMaterial {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String ticketHash;
    private final AdminPasswordResetLinkResponse response;

    private PasswordResetLinkMaterial(String ticketHash, AdminPasswordResetLinkResponse response) {
        this.ticketHash = ticketHash;
        this.response = response;
    }

    static PasswordResetLinkMaterial create(String recoveryUrl, Instant now) {
        String base = trustedRecoveryUrl(recoveryUrl);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = now.plusSeconds(3600);
        return new PasswordResetLinkMaterial(
                hash(ticket),
                new AdminPasswordResetLinkResponse(
                        base + "#resetTicket=" + ticket + "&expiresAt=" + expiresAt,
                        expiresAt,
                        3600));
    }

    String ticketHash() {
        return ticketHash;
    }

    AdminPasswordResetLinkResponse response() {
        return response;
    }

    @Override
    public String toString() {
        return "PasswordResetLinkMaterial[<redacted>]";
    }

    private static String trustedRecoveryUrl(String recoveryUrl) {
        try {
            URI uri = URI.create(recoveryUrl);
            String host = uri.getHost();
            boolean loopback =
                    "localhost".equalsIgnoreCase(host)
                            || "127.0.0.1".equals(host)
                            || "[::1]".equals(host);
            if (host == null
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || uri.getPath() == null
                    || !(uri.getPath().endsWith("/password-reset")
                            || uri.getPath().endsWith("/password-reset/"))
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || (loopback && "http".equalsIgnoreCase(uri.getScheme())))) {
                throw new IllegalArgumentException("invalid configured recovery URL");
            }
            return uri.toASCIIString();
        } catch (RuntimeException exception) {
            // Configuration failures never echo the configured value or incoming request headers.
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private static String hash(String ticket) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
