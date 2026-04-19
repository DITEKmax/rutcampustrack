package ru.rutcampustrack.gateway.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * M03a: config for {@code InternalJwtIssuerClient} — WebClient parameters
 * and cache tuning for token-exchange calls to auth-service.
 */
@ConfigurationProperties(prefix = "rutcampustrack.security.internal-issuer-client")
public class InternalIssuerClientProperties {

    public static final int MIN_SECRET_LENGTH = 32;

    private String authServiceUrl = "http://auth-service:9090";
    private String secret;
    private long cacheTtlSeconds = 240;
    private int cacheMaxSize = 10_000;
    private long timeoutMillis = 3_000;

    public String getAuthServiceUrl() {
        return authServiceUrl;
    }

    public void setAuthServiceUrl(String authServiceUrl) {
        this.authServiceUrl = authServiceUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }

    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "rutcampustrack.security.internal-issuer-client.secret is required "
                            + "(INTERNAL_ISSUER_SECRET env var). Must match auth-service secret, "
                            + "at least " + MIN_SECRET_LENGTH + " bytes.");
        }
        if (secret.getBytes().length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Internal issuer secret must be at least " + MIN_SECRET_LENGTH + " bytes");
        }
        if (cacheTtlSeconds <= 0 || cacheTtlSeconds > 290) {
            // TTL must be strictly less than auth-service token TTL (300) so we never
            // serve an expired token from cache (5-sec safety margin already baked in at 290).
            throw new IllegalStateException(
                    "cache-ttl-seconds must be in (0, 290] to stay under auth-service token TTL");
        }
    }
}
