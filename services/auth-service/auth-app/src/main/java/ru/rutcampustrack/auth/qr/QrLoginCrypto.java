package ru.rutcampustrack.auth.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public final class QrLoginCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private QrLoginCrypto() {}
    public static String secret() {
        byte[] value = new byte[32]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public static byte[] issuerHash(String secret) { return hash("qr:issuer:" + secret); }
    public static byte[] approvalHash(UUID challenge, String token) { return hash("qr:LOGIN:" + challenge + ":" + token); }
    public static byte[] refreshHash(UUID jti) { return hash("qr:refresh:" + jti); }
    public static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    static boolean equal(byte[] expected, byte[] actual) { return MessageDigest.isEqual(expected, actual); }
}
