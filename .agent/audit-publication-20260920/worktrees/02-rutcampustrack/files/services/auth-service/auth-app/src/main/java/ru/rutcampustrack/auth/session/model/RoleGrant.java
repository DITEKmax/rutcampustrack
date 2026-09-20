package ru.rutcampustrack.auth.session.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, server-owned membership of one user in one role. The identity
 * fields are deliberately explicit so an adapter cannot silently rebind a
 * session grant to another user.
 */
public record RoleGrant(
        long grantId,
        long userId,
        AuthRole role,
        RoleStatus status,
        Long groupId,
        Instant createdAt,
        Instant updatedAt
) {

    public RoleGrant {
        if (grantId <= 0) {
            throw new IllegalArgumentException("grantId must be positive");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        role = Objects.requireNonNull(role, "role");
        status = Objects.requireNonNull(status, "status");
        if (groupId != null && groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive when present");
        }
        if (role == AuthRole.HEADMAN && groupId == null) {
            throw new IllegalArgumentException("HEADMAN grant requires groupId");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    public boolean belongsTo(long candidateUserId) {
        return userId == candidateUserId;
    }

    public boolean isSelectable() {
        return status.isSelectable();
    }

    public boolean isReadOnly() {
        return status.isReadOnly();
    }
}
