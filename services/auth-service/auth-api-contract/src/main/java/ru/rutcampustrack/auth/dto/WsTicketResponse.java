package ru.rutcampustrack.auth.dto;

import java.time.Instant;

/**
 * M03b Группа 3: short-lived single-use ticket для WebSocket handshake.
 * Клиент вызывает {@code new WebSocket("wss://.../ws?ticket=<ticket>")},
 * notification-web consume'ит ticket через token-exchange endpoint.
 */
public record WsTicketResponse(
        String ticket,
        Instant expiresAt
) {
}
