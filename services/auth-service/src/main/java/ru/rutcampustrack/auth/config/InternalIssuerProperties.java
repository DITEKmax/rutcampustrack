package ru.rutcampustrack.auth.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Shared secret config for {@code POST /internal/issue-internal-jwt}.
 *
 * Called by api-gateway via {@code X-Internal-Issuer-Secret} header. Fail-fast
 * on startup if empty — mirrors {@code GRPC_SECRET} pattern for defence in depth.
 */
@ConfigurationProperties(prefix = "rutcampustrack.security.internal-issuer")
public class InternalIssuerProperties {

    /**
     * Minimum secret length in bytes — NIST recommends ≥ 32 for HMAC-like secrets.
     */
    public static final int MIN_SECRET_LENGTH = 32;

    private String secret;
    private long tokenTtlSeconds = 300;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getTokenTtlSeconds() {
        return tokenTtlSeconds;
    }

    public void setTokenTtlSeconds(long tokenTtlSeconds) {
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "rutcampustrack.security.internal-issuer.secret is required " +
                            "(INTERNAL_ISSUER_SECRET env var). Must be at least "
                            + MIN_SECRET_LENGTH + " bytes.");
        }
        if (secret.getBytes().length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "rutcampustrack.security.internal-issuer.secret must be at least "
                            + MIN_SECRET_LENGTH + " bytes (got " + secret.getBytes().length + ").");
        }
        if (tokenTtlSeconds <= 0 || tokenTtlSeconds > 3600) {
            throw new IllegalStateException(
                    "rutcampustrack.security.internal-issuer.token-ttl-seconds must be in (0, 3600], "
                            + "got " + tokenTtlSeconds);
        }
    }
}
