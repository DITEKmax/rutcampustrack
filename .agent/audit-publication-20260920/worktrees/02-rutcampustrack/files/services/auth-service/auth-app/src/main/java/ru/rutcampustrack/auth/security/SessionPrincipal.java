package ru.rutcampustrack.auth.security;

import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.util.Objects;
import java.util.UUID;

/** Immutable JWT identity carried into protected public session operations. */
public record SessionPrincipal(
        long userId,
        UUID sessionId,
        long sessionVersion,
        long rolesVersion,
        AuthRole selectedRole,
        RoleStatus selectedStatus,
        Long groupId,
        boolean headman,
        boolean readOnly
) {
    public SessionPrincipal {
        if (userId <= 0 || sessionVersion <= 0 || rolesVersion <= 0) {
            throw new IllegalArgumentException("session identity values must be positive");
        }
        sessionId = Objects.requireNonNull(sessionId, "sessionId");
        if (selectedRole == null) {
            if (selectedStatus != null || groupId != null || headman || readOnly) {
                throw new IllegalArgumentException("bootstrap principal cannot carry selected identity");
            }
        } else {
            selectedStatus = Objects.requireNonNull(selectedStatus, "selectedStatus");
            if (groupId != null && groupId <= 0) {
                throw new IllegalArgumentException("groupId must be positive");
            }
            if (headman != (selectedRole == AuthRole.HEADMAN)
                    || readOnly != selectedStatus.isReadOnly()) {
                throw new IllegalArgumentException("selected identity is inconsistent");
            }
        }
    }

    public boolean isBootstrap() {
        return selectedRole == null;
    }

    @Override
    public String toString() {
        return Long.toString(userId);
    }
}
