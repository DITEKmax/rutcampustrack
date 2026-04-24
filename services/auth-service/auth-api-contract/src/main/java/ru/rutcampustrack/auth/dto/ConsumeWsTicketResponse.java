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
        String role,
        @JsonProperty("group_id") long groupId,
        @JsonProperty("is_headman") boolean isHeadman,
        @JsonProperty("expires_at") Instant expiresAt
) {
}
