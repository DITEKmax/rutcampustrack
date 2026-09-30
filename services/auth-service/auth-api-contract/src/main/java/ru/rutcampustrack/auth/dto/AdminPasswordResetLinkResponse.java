package ru.rutcampustrack.auth.dto;

import java.time.Instant;

/** The URL is bearer credential material and must never be logged or persisted by consumers. */
public record AdminPasswordResetLinkResponse(String url, Instant expiresAt, int expiresInSeconds) {
    @Override
    public String toString() {
        return "AdminPasswordResetLinkResponse[url=<redacted>, expiresAt=" + expiresAt
                + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
