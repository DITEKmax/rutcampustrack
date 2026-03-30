package ru.rutcampustrack.gateway.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.util.retry.Retry;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class PublicKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(PublicKeyConfig.class);

    @Value("${gateway.auth-service-url:http://auth-service:9090}")
    private String authServiceUrl;

    private final AtomicReference<PublicKey> publicKeyRef = new AtomicReference<>();
    private final WebClient webClient = WebClient.create();

    @PostConstruct
    public void init() {
        fetchAndCachePublicKey();
    }

    @Scheduled(fixedRate = 3_600_000)
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

    private void fetchAndCachePublicKey() {
        try {
            PublicKeyResponse response = webClient.get()
                    .uri(authServiceUrl + "/auth/public-key")
                    .retrieve()
                    .bodyToMono(PublicKeyResponse.class)
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(5))
                            .filter(e -> e instanceof WebClientRequestException))
                    .block(Duration.ofSeconds(30));

            if (response == null || response.publicKey() == null) {
                throw new IllegalStateException("Auth Service returned empty public key");
            }

            PublicKey key = parsePemPublicKey(response.publicKey());
            publicKeyRef.set(key);
            log.info("RSA public key fetched and cached from Auth Service");

        } catch (Exception e) {
            log.error("Failed to fetch public key from Auth Service: {}", e.getMessage());
            // Do not crash on refresh failure — keep existing key cached
        }
    }

    PublicKey parsePemPublicKey(String pem) throws Exception {
        String stripped = pem
                .replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    record PublicKeyResponse(String publicKey, String algorithm) {}
}
