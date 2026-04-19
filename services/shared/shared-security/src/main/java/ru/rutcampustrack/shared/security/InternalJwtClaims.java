package ru.rutcampustrack.shared.security;

/**
 * Structured claims extracted from a validated Internal JWT.
 *
 * Downstream services map this into their own {@code RequestContext}
 * (which depends on service-specific {@code UserRole} enum from contract module).
 */
public record InternalJwtClaims(
        Long userId,
        String role,
        Long groupId,
        boolean isHeadman
) {
}
