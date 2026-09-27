package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * M12 G3: extracted from InternalWsTicketController nested record.
 * Returned by notification-web TicketHandshakeInterceptor after atomic GET+DEL
 * на Redis ws-ticket. Preserves snake_case JSON shape (binary-compatible).
 */
public record ConsumeWsTicketResponse(
        @JsonProperty("user_id") long userId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("session_version") long sessionVersion,
        @JsonProperty("roles_version") long rolesVersion,
        String role,
        String status,
        @JsonProperty("group_id") Long groupId,
        @JsonProperty("is_headman") boolean isHeadman,
        @JsonProperty("read_only") boolean readOnly,
        @JsonProperty("expires_at") Instant expiresAt
) {
    public WsSessionAdmissionRequest admissionRequest() {
        return new WsSessionAdmissionRequest(userId, sessionId, sessionVersion, rolesVersion,
                role, status, groupId, isHeadman, readOnly);
    }
}
