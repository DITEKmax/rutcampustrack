package ru.rutcampustrack.gateway.config;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

@Component
public class PublicKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(PublicKeyConfig.class);
    private static final long REFRESH_NANOS = Duration.ofHours(1).toNanos();
    private static final long RETRY_NANOS = Duration.ofSeconds(30).toNanos();

    @Value("${gateway.auth-service-url:http://auth-service:9090}")
    private String authServiceUrl;

    private final AtomicReference<PublicKey> publicKeyRef = new AtomicReference<>();
    private final AtomicBoolean fetchInProgress = new AtomicBoolean();
    private final WebClient webClient;
    private final LongSupplier nanoTime;
    // Accessed only by the holder of fetchInProgress; CAS also publishes the deadline.
    private boolean fetchScheduled;
    private long nextFetchAtNanos;

    public PublicKeyConfig() {
        this(WebClient.create(), null, System::nanoTime);
    }

    PublicKeyConfig(WebClient webClient, String authServiceUrl, LongSupplier nanoTime) {
        this.webClient = webClient;
        this.authServiceUrl = authServiceUrl;
        this.nanoTime = nanoTime;
    }

    @PostConstruct
    public void init() {
        fetchAndCachePublicKey();
    }

    // NEW-28: single-instance by design — ShedLock would break key rotation.
    // Каждый gateway-инстанс держит свой AtomicReference<PublicKey>. При
    // ShedLock-координации кэш остальных инстансов протухнет после
    // ротации ключа в Auth (C0-1, M03), и они начнут отвергать валидные JWT.
    // A failed cold start must recover quickly; successful fetches remain hourly.
    @Scheduled(fixedDelay = 30_000)
    @SuppressWarnings("SingleInstance")
    public void refresh() {
        fetchAndCachePublicKey();
    }

    public PublicKey getPublicKey() {
        PublicKey key = publicKeyRef.get();
        if (key == null) {
            throw new IllegalStateException("Public key not yet loaded from Auth Service");
        }
        return key;
    }

    /**
     * KI-4 (M03b backlog) — non-throwing readiness probe для {@code PublicKeyHealthIndicator}.
     * Возвращает {@code false} вместо исключения, чтобы health-endpoint не падал на 500.
     */
    public boolean isReady() {
        return publicKeyRef.get() != null;
    }

    private void fetchAndCachePublicKey() {
        if (!fetchInProgress.compareAndSet(false, true)) {
            return;
        }
        try {
            if (fetchScheduled && nanoTime.getAsLong() - nextFetchAtNanos < 0) {
                return;
            }
            PublicKeyResponse response =
                    webClient
                            .get()
                            .uri(authServiceUrl + "/auth/public-key")
                            .retrieve()
                            .bodyToMono(PublicKeyResponse.class)
                            .retryWhen(
                                    Retry.fixedDelay(3, Duration.ofSeconds(5))
                                            .filter(e -> e instanceof WebClientRequestException))
                            .block(Duration.ofSeconds(30));

            if (response == null || response.publicKey() == null) {
                throw new IllegalStateException("Auth Service returned empty public key");
            }

            PublicKey key = parsePemPublicKey(response.publicKey());
            publicKeyRef.set(key);
            scheduleNextFetch(REFRESH_NANOS);
            log.info("RSA public key fetched and cached from Auth Service");

        } catch (Exception e) {
            log.error("Failed to fetch public key from Auth Service: {}", e.getMessage());
            // Do not crash on refresh failure — keep existing key cached
            scheduleNextFetch(RETRY_NANOS);
        } finally {
            fetchInProgress.set(false);
        }
    }

    private void scheduleNextFetch(long delayNanos) {
        nextFetchAtNanos = nanoTime.getAsLong() + delayNanos;
        fetchScheduled = true;
    }

    PublicKey parsePemPublicKey(String pem) throws Exception {
        String stripped =
                pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                        .replaceAll("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    record PublicKeyResponse(String publicKey, String algorithm) {}
}
