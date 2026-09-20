package ru.rutcampustrack.shared.security;

import java.util.UUID;

/**
 * Structured claims extracted from a validated Internal JWT.
 *
 * Downstream services map this into their own {@code RequestContext}
 * (which depends on service-specific {@code UserRole} enum from contract module).
 */
public record InternalJwtClaims(
        long userId,
        UUID sessionId,
        long sessionVersion,
        long rolesVersion,
        String role,
        String status,
        Long groupId,
        boolean isHeadman,
        boolean readOnly
) {
    /**
     * Returns the service-domain role represented by this signed identity.
     *
     * <p>{@code HEADMAN} is an Auth/session role, while downstream service
     * contracts model the same user as {@code STUDENT} plus the separate
     * {@code isHeadman} capability flag.  The raw role remains available for
     * admission and identity comparisons.</p>
     */
    public String domainRole() {
        if ("HEADMAN".equals(role)) {
            if (!isHeadman || groupId == null || groupId <= 0) {
                throw new IllegalStateException("Invalid HEADMAN identity");
            }
            return "STUDENT";
        }
        return role;
    }
}
