package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AuthAdmissionResponse")
public record AuthAdmissionResponse(
        String internalToken,
        Instant expiresAt,
        String sessionId,
        String userId,
        String sessionVersion,
        String rolesVersion,
        String role,
        String status,
        @Schema(nullable = true) String groupId,
        boolean isHeadman,
        boolean readOnly
) {
    public AuthAdmissionResponse {
        Objects.requireNonNull(internalToken, "internalToken");
        if (internalToken.isBlank()) {
            throw new IllegalArgumentException("internalToken must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt");
        requireUuid(sessionId, "sessionId");
        requirePositiveDecimal(userId, "userId");
        requirePositiveDecimal(sessionVersion, "sessionVersion");
        requirePositiveDecimal(rolesVersion, "rolesVersion");
        requireUppercase(role, "role");
        requireUppercase(status, "status");
        if (groupId != null) {
            requirePositiveDecimal(groupId, "groupId");
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

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
    @Override
    public String toString() {
        return "AuthAdmissionResponse[internalToken=<redacted>, expiresAt=" + expiresAt
                + ", sessionId=" + sessionId
                + ", userId=" + userId
                + ", sessionVersion=" + sessionVersion
                + ", rolesVersion=" + rolesVersion
                + ", role=" + role
                + ", status=" + status
                + ", groupId=" + groupId
                + ", isHeadman=" + isHeadman
                + ", readOnly=" + readOnly
                + "]";
    }
}
