package ru.rutcampustrack.auth.session.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal session transition state. Refresh JTIs are intentionally kept out
 * of public snapshots and redacted from this record's textual form.
 */
public record SessionState(
        UUID sessionId,
        long userId,
        Long activeRoleGrantId,
        long sessionVersion,
        UUID currentRefreshJti,
        UUID previousRefreshJti,
        Instant refreshExpiresAt,
        Instant createdAt,
        Instant lastSeenAt,
        Instant revokedAt,
        SessionRevokeReason revokeReason,
        AuthMethod authMethod,
        String clientLabel,
        String locationLabel
) {

    public SessionState {
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (activeRoleGrantId != null && activeRoleGrantId <= 0) {
            throw new IllegalArgumentException("activeRoleGrantId must be positive when present");
        }
        if (sessionVersion <= 0) {
            throw new IllegalArgumentException("sessionVersion must be positive");
        }
        currentRefreshJti = Objects.requireNonNull(currentRefreshJti, "currentRefreshJti");
        if (Objects.equals(currentRefreshJti, previousRefreshJti)) {
            throw new IllegalArgumentException("previousRefreshJti must differ from currentRefreshJti");
        }
        refreshExpiresAt = Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (!refreshExpiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("refreshExpiresAt must be after createdAt");
        }
        if (lastSeenAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastSeenAt must not precede createdAt");
        }
        if ((revokedAt == null) != (revokeReason == null)) {
            throw new IllegalArgumentException("revokedAt and revokeReason must be both null or both present");
        }
        if (revokedAt != null && revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("revokedAt must not precede createdAt");
        }
        authMethod = Objects.requireNonNull(authMethod, "authMethod");
        validateLabel(clientLabel, "clientLabel");
        validateLabel(locationLabel, "locationLabel");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isRefreshExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(refreshExpiresAt);
    }

    public boolean isLiveAt(Instant now) {
        return !isRevoked() && !isRefreshExpiredAt(now);
    }

    public SessionState withActiveRole(Long grantId, long nextVersion) {
        return new SessionState(
                sessionId, userId, grantId, nextVersion, currentRefreshJti,
                previousRefreshJti, refreshExpiresAt, createdAt, lastSeenAt,
                revokedAt, revokeReason, authMethod, clientLabel, locationLabel
        );
    }

    public SessionState withRefresh(UUID nextCurrent, Instant seenAt) {
        Objects.requireNonNull(nextCurrent, "nextCurrent");
        Objects.requireNonNull(seenAt, "seenAt");
        if (!seenAt.isBefore(refreshExpiresAt) && !seenAt.equals(refreshExpiresAt)) {
            throw new IllegalArgumentException("seenAt must not be after refresh expiry");
        }
        return new SessionState(
                sessionId, userId, activeRoleGrantId, sessionVersion, nextCurrent,
                currentRefreshJti, refreshExpiresAt, createdAt, seenAt,
                revokedAt, revokeReason, authMethod, clientLabel, locationLabel
        );
    }

    public SessionState revokedAt(Instant when, SessionRevokeReason reason) {
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(reason, "reason");
        if (when.isBefore(createdAt)) {
            throw new IllegalArgumentException("revocation must not precede creation");
        }
        return new SessionState(
                sessionId, userId, activeRoleGrantId, sessionVersion, currentRefreshJti,
                previousRefreshJti, refreshExpiresAt, createdAt, lastSeenAt,
                when, reason, authMethod, clientLabel, locationLabel
        );
    }

    @Override
    public String toString() {
        return "SessionState[sessionId=" + sessionId
                + ", userId=" + userId
                + ", activeRoleGrantId=" + activeRoleGrantId
                + ", sessionVersion=" + sessionVersion
                + ", refreshJti=<redacted>"
                + ", refreshExpiresAt=" + refreshExpiresAt
                + ", createdAt=" + createdAt
                + ", lastSeenAt=" + lastSeenAt
                + ", revokedAt=" + revokedAt
                + ", revokeReason=" + revokeReason
                + ", authMethod=" + authMethod
                + ", clientLabel=" + clientLabel
                + ", locationLabel=" + locationLabel + ']';
    }

    private static void validateLabel(String value, String field) {
        if (value != null && value.length() > 160) {
            throw new IllegalArgumentException(field + " must be at most 160 characters");
        }
    }
}
