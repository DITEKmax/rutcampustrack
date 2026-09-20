package ru.rutcampustrack.auth.session.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only account security metadata; no secrets or raw token material. */
public record SecurityEvent(
        long userId,
        UUID sessionId,
        Type type,
        Instant occurredAt,
        AuthMethod authMethod,
        String clientLabel,
        String locationLabel
) {

    public enum Type {
        LOGIN,
        ROLE_CHANGED,
        CURRENT_LOGOUT,
        LOGOUT_ALL,
        PASSWORD_CHANGED,
        SECURITY_REVOKED
    }

    public SecurityEvent {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        type = Objects.requireNonNull(type, "type");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        validateLabel(clientLabel, "clientLabel");
        validateLabel(locationLabel, "locationLabel");
    }

    private static void validateLabel(String value, String field) {
        if (value != null && value.length() > 160) {
            throw new IllegalArgumentException(field + " must be at most 160 characters");
        }
    }
}
