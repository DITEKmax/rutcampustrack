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
}
