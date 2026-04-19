package ru.rutcampustrack.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for Internal JWT validation on downstream services.
 *
 * Bind with {@code @EnableConfigurationProperties(InternalJwtProperties.class)}
 * in the service application class.
 */
@ConfigurationProperties(prefix = "rutcampustrack.security.internal-jwt")
public record InternalJwtProperties(
        String authServiceUrl,
        int publicKeyRefreshMinutes,
        int clockSkewSeconds,
        boolean legacyHeadersEnabled,
        String expectedIssuer,
        String expectedAudience,
        String headerName
) {
    public InternalJwtProperties {
        if (authServiceUrl == null || authServiceUrl.isBlank()) {
            authServiceUrl = "http://auth-service:9090";
        }
        if (publicKeyRefreshMinutes <= 0) {
            publicKeyRefreshMinutes = 60;
        }
        if (clockSkewSeconds < 0) {
            clockSkewSeconds = 30;
        }
        if (expectedIssuer == null || expectedIssuer.isBlank()) {
            expectedIssuer = "rutcampustrack-auth";
        }
        if (expectedAudience == null || expectedAudience.isBlank()) {
            expectedAudience = "rutcampustrack-internal";
        }
        if (headerName == null || headerName.isBlank()) {
            headerName = "X-Internal-Token";
        }
    }
}
