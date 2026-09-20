package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AuthSessionSummary")
public record AuthSessionSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sessionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String authMethod,
        @Schema(nullable = true) String clientLabel,
        @Schema(nullable = true) String locationLabel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant lastSeenAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean current
) {
    public AuthSessionSummary {
        requireUuid(sessionId, "sessionId");
        requireUppercase(authMethod, "authMethod");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        if (lastSeenAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastSeenAt must not precede createdAt");
        }
    }

    private static void requireUuid(String value, String name) {
        Objects.requireNonNull(value, name);
        UUID parsed;
        try {
            parsed = UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(name + " must be a UUID string", exception);
        }
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException(name + " must be a canonical lowercase UUID string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}
