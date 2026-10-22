package ru.rutcampustrack.auth.dto;

import java.time.Instant;

/**
 * Response from {@code POST /internal/issue-internal-jwt} — signed Internal JWT + its expiration.
 */
public record InternalIssueResponse(String token, Instant expiresAt) {
    @Override
    public String toString() {
        return "InternalIssueResponse[token=<redacted>, expiresAt=" + expiresAt + ']';
    }
}
