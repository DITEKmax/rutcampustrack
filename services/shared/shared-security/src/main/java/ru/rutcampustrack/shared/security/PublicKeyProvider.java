package ru.rutcampustrack.shared.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pulls the RSA public key from auth-service {@code /auth/public-key} endpoint
 * and caches it in memory. Refreshed hourly (configurable via
 * {@link InternalJwtProperties#publicKeyRefreshMinutes()}).
 *
 * Uses {@link RestClient} — servlet-friendly, works in both Spring MVC and WebFlux stacks.
 * Each downstream instance holds its own {@link AtomicReference}, no distributed lock
 * needed (per-instance cache by design; coordinated refresh would break key rotation).
 */
public class PublicKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(PublicKeyProvider.class);

    private final InternalJwtProperties properties;
    private final RestClient restClient;
    private final AtomicReference<PublicKey> publicKeyRef = new AtomicReference<>();

    public PublicKeyProvider(InternalJwtProperties properties) {
        this(properties, RestClient.builder().baseUrl(properties.authServiceUrl()).build());
    }

    PublicKeyProvider(InternalJwtProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @PostConstruct
    public void init() {
        fetchAndCache();
    }

    // NEW-28 / M02: per-instance cache by design. ShedLock would break key rotation
    // (other instances would serve a stale key and reject valid JWTs after rotation).
    @Scheduled(fixedRateString = "#{${rutcampustrack.security.internal-jwt.public-key-refresh-minutes:60} * 60 * 1000}")
    @SuppressWarnings("SingleInstance")
    public void refresh() {
        fetchAndCache();
    }

    public PublicKey getPublicKey() {
        PublicKey key = publicKeyRef.get();
        if (key == null) {
            throw new IllegalStateException("Public key not yet loaded from Auth Service");
        }
        return key;
    }

    private void fetchAndCache() {
        try {
            PublicKeyResponse response = restClient.get()
                    .uri("/auth/public-key")
                    .retrieve()
                    .body(PublicKeyResponse.class);

            if (response == null || response.publicKey() == null) {
                throw new IllegalStateException("Auth Service returned empty public key");
            }

            PublicKey key = parsePemPublicKey(response.publicKey());
            publicKeyRef.set(key);
            log.info("Internal JWT public key fetched from {}", properties.authServiceUrl());

        } catch (ResourceAccessException e) {
            log.error("Auth Service unreachable: {}", e.getMessage());
            // Do not crash on refresh failure — keep existing key cached (may be null on first run).
        } catch (Exception e) {
            log.error("Failed to fetch public key from Auth Service: {}", e.getMessage());
        }
    }

    static PublicKey parsePemPublicKey(String pem) throws Exception {
        String stripped = pem
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    record PublicKeyResponse(String publicKey, String algorithm) {
    }
}
