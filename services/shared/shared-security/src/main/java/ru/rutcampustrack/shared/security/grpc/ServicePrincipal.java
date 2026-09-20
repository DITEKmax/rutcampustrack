package ru.rutcampustrack.shared.security.grpc;

import io.grpc.Context;

import java.util.Objects;

/**
 * Immutable identity of a trusted service caller.
 *
 * <p>This type is deliberately separate from {@code InternalJwtClaims}. A
 * service credential authenticates the service at the transport boundary; it
 * does not carry a user, session, or domain actor identity.</p>
 */
public final class ServicePrincipal {

    public static final ServicePrincipal ACADEMIC_SERVICE =
            new ServicePrincipal("academic-service");
    public static final ServicePrincipal SCHEDULE_SERVICE =
            new ServicePrincipal("schedule-service");

    /** Context key populated only after a protected call has been verified. */
    public static final Context.Key<ServicePrincipal> CONTEXT_KEY =
            Context.key("rutcampustrack.verified-service-principal");

    private final String name;

    public ServicePrincipal(String name) {
        if (name == null || name.isBlank() || name.length() > 64
                || !name.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid service principal");
        }
        this.name = name;
    }

    public String name() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ServicePrincipal that)) {
            return false;
        }
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "ServicePrincipal{" + name + "}";
    }
}
