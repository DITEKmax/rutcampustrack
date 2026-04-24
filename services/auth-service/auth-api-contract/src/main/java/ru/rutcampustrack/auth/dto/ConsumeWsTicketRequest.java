package ru.rutcampustrack.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * M12 G3: extracted from InternalWsTicketController nested record.
 * Internal endpoint payload — consumed by notification-web during WS handshake.
 */
public record ConsumeWsTicketRequest(@NotBlank String ticket) {
}
