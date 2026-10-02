package ru.rutcampustrack.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record QrLoginIssueResponse(UUID issuerId, String issuerSecret, UUID challengeId,
        QrLoginPurpose purpose, String qrPayload, Instant expiresAt, long ttl, int pollAfterSeconds) {
    @Override public String toString() {
        return "QrLoginIssueResponse[issuerId=" + issuerId + ", challengeId=" + challengeId
                + ", issuerSecret=<redacted>, qrPayload=<redacted>, expiresAt=" + expiresAt + ']';
    }
}
