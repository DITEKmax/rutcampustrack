package ru.rutcampustrack.academic.map;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Produces the pseudonymous owner key for map-open dedupe rows.
 *
 * <p>The key is deliberately separate from JWT and gRPC credentials.  An
 * absent or weak production setting disables writes while leaving map reads
 * available.</p>
 */
@Component
public class CampusMapOwnerHmacProvider {
    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] DOMAIN = "rutcampustrack:campus-map-owner:v1:"
            .getBytes(StandardCharsets.UTF_8);
    private static final int MIN_KEY_BYTES = 32;

    private final byte[] key;

    public CampusMapOwnerHmacProvider(CampusMapUsageProperties properties) {
        String configured = properties == null ? null : properties.hmacKey();
        this.key = configured == null || configured.isBlank()
                ? null
                : configured.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] forUser(long userId) {
        if (userId <= 0) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.PERMISSION_DENIED,
                    "signed campus-map identity is not allowed");
        }
        if (key == null || key.length < MIN_KEY_BYTES) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.UNAVAILABLE,
                    "campus map usage is not configured");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(ByteBuffer.allocate(DOMAIN.length + Long.BYTES)
                    .put(DOMAIN)
                    .putLong(userId)
                    .array());
        } catch (Exception error) {
            throw CampusMapReadException.of(
                    CampusMapReadException.Code.INTERNAL,
                    "campus map usage identity hashing failed",
                    error);
        }
    }
}
