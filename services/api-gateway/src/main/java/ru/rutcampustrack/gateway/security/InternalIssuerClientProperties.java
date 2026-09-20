package ru.rutcampustrack.gateway.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the per-request auth admission client.
 */
@ConfigurationProperties(prefix = "rutcampustrack.security.internal-issuer-client")
public class InternalIssuerClientProperties {

    public static final int MIN_SECRET_LENGTH = 32;

    private String authServiceUrl = "http://auth-service:9090";
    private String secret;
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
        if (timeoutMillis <= 0) {
            throw new IllegalStateException("timeout-millis must be positive");
        }
    }
}
