package ru.rutcampustrack.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tma")
public record TmaProperties(
    String botToken,
    long authDateMaxAgeSeconds
) {}
