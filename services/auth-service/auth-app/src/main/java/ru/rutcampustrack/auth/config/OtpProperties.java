package ru.rutcampustrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otp")
public record OtpProperties(
    int codeLength,
    int ttlSeconds,
    int maxAttempts,
    int attemptsWindowSeconds,
    int resendCooldownSeconds
) {}
