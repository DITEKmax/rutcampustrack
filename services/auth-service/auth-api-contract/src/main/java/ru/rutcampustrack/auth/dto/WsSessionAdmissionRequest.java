package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Objects;
import java.util.UUID;

/** Session identity used by trusted internal WebSocket admission calls. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "WsSessionAdmissionRequest")
public record WsSessionAdmissionRequest(
        @JsonProperty("user_id") @Positive long userId,
        @JsonProperty("session_id") @NotBlank String sessionId,
        @JsonProperty("session_version") @Positive long sessionVersion,
        @JsonProperty("roles_version") @Positive long rolesVersion,
        @NotBlank String role,
        @NotBlank String status,
        @JsonProperty("group_id") @Positive Long groupId,
        @JsonProperty("is_headman") boolean isHeadman,
        @JsonProperty("read_only") boolean readOnly
) {
    public WsSessionAdmissionRequest {
        if (userId <= 0 || sessionVersion <= 0 || rolesVersion <= 0) {
            throw new IllegalArgumentException("session identity values must be positive");
        }
        Objects.requireNonNull(sessionId, "sessionId");
        UUID parsedSessionId;
        try {
            parsedSessionId = UUID.fromString(sessionId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sessionId must be a UUID", exception);
        }
        if (!parsedSessionId.toString().equals(sessionId)) {
            throw new IllegalArgumentException("sessionId must be a canonical lowercase UUID");
        }
        requireWireValue(role, "role");
        requireWireValue(status, "status");
        if (groupId != null && groupId <= 0) {
            throw new IllegalArgumentException("groupId must be positive when present");
        }
        if (isHeadman != "HEADMAN".equals(role)) {
            throw new IllegalArgumentException("headman flag must match role");
        }
    }

    private static void requireWireValue(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}
