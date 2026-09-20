package ru.rutcampustrack.auth.session.model;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Safe session view with no refresh JTI, credential hash, or bearer material. */
public record SessionSnapshot(
        UUID sessionId,
        long userId,
        long sessionVersion,
        long rolesVersion,
        RoleGrant activeRole,
        List<RoleGrant> roles,
        Instant refreshExpiresAt,
        Instant createdAt,
        Instant lastSeenAt,
        Instant revokedAt,
        SessionRevokeReason revokeReason,
        AuthMethod authMethod,
        String clientLabel,
        String locationLabel
) {

    public SessionSnapshot {
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        if (sessionVersion <= 0 || rolesVersion <= 0) {
            throw new IllegalArgumentException("versions must be positive");
        }
        roles = List.copyOf(Objects.requireNonNull(roles, "roles"));
        Set<Long> grantIds = new HashSet<>();
        Set<AuthRole> roleKinds = new HashSet<>();
        for (RoleGrant grant : roles) {
            Objects.requireNonNull(grant, "roles cannot contain null");
            if (!grant.belongsTo(userId)) {
                throw new IllegalArgumentException("role grant belongs to another user");
            }
            if (!grantIds.add(grant.grantId()) || !roleKinds.add(grant.role())) {
                throw new IllegalArgumentException("roles cannot contain duplicate grant or role");
            }
        }
        if (activeRole != null) {
            if (!activeRole.belongsTo(userId)) {
                throw new IllegalArgumentException("active role belongs to another user");
            }
            if (roles.stream().noneMatch(activeRole::equals)) {
                throw new IllegalArgumentException("active role must exactly match the roles snapshot");
            }
            if (!activeRole.isSelectable()) {
                throw new IllegalArgumentException("active role must be selectable");
            }
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

    public boolean isReadOnly() {
        return activeRole != null && activeRole.isReadOnly();
    }

    public boolean isLiveAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !isRevoked() && now.isBefore(refreshExpiresAt);
    }

    private static void validateLabel(String value, String field) {
        if (value != null && value.length() > 160) {
            throw new IllegalArgumentException(field + " must be at most 160 characters");
        }
    }
}
