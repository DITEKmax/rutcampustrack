package ru.rutcampustrack.shared.security.grpc;

import io.grpc.Metadata;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

/**
 * A configured opaque credential directed from one service to one receiver.
 *
 * <p>The wire representation is exactly 32 bytes encoded as unpadded
 * URL-safe base64 (43 ASCII characters). The encoded value is never included
 * in this type's error messages or {@link #toString()}.</p>
 */
public final class DirectedServiceCredential {

    public static final int TOKEN_BYTES = 32;
    public static final int TOKEN_CHARACTERS = 43;
    public static final Metadata.Key<String> TOKEN_METADATA_KEY =
            Metadata.Key.of("x-service-token", Metadata.ASCII_STRING_MARSHALLER);

    private final ServicePrincipal source;
    private final ServicePrincipal target;
    private final String encodedToken;
    private final byte[] tokenBytes;

    public DirectedServiceCredential(ServicePrincipal source, ServicePrincipal target, String encodedToken) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Service credential direction is required");
        }
        byte[] decoded = decodeCanonical(encodedToken);
        if (decoded == null) {
            throw new IllegalArgumentException("Invalid service credential configuration");
        }
        this.source = source;
        this.target = target;
        this.encodedToken = encodedToken;
        this.tokenBytes = decoded;
    }

    /**
     * Parses externally supplied configuration without turning malformed
     * configuration into a permissive boundary. Empty is an explicit
     * fail-closed state for the receiver.
     */
    public static Optional<DirectedServiceCredential> tryCreate(
            ServicePrincipal source,
            ServicePrincipal target,
            String encodedToken) {
        if (source == null || target == null || decodeCanonical(encodedToken) == null) {
            return Optional.empty();
        }
        return Optional.of(new DirectedServiceCredential(source, target, encodedToken));
    }

    public static boolean isCanonicalToken(String encodedToken) {
        return decodeCanonical(encodedToken) != null;
    }

    public ServicePrincipal source() {
        return source;
    }

    public ServicePrincipal target() {
        return target;
    }

    /** Package-private so only the narrow CallCredentials primitive emits it. */
    String wireToken() {
        return encodedToken;
    }

    /**
     * Compares a received token without converting an accepted token into a
     * caller-controlled principal. Invalid encodings are rejected before the
     * constant-time byte comparison.
     */
    boolean matches(String candidate) {
        byte[] candidateBytes = decodeCanonical(candidate);
        return candidateBytes != null && MessageDigest.isEqual(tokenBytes, candidateBytes);
    }

    @Override
    public String toString() {
        return "DirectedServiceCredential{" + source.name() + "->" + target.name() + "}";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DirectedServiceCredential that)) {
            return false;
        }
        return source.equals(that.source)
                && target.equals(that.target)
                && Arrays.equals(tokenBytes, that.tokenBytes);
    }

    @Override
    public int hashCode() {
        int result = 31 * source.hashCode() + target.hashCode();
        return 31 * result + Arrays.hashCode(tokenBytes);
    }

    private static byte[] decodeCanonical(String encodedToken) {
        if (encodedToken == null || encodedToken.length() != TOKEN_CHARACTERS
                || !encodedToken.matches("[A-Za-z0-9_-]{43}")) {
            return null;
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedToken);
            if (decoded.length != TOKEN_BYTES) {
                return null;
            }
            String canonical = Base64.getUrlEncoder().withoutPadding().encodeToString(decoded);
            return canonical.equals(encodedToken) ? decoded : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
