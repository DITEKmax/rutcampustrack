package ru.rutcampustrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String keyDir,
    long accessTokenExpiration,
    long refreshTokenExpiration
) {}
