package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.WsTicketResponse;

/**
 * M12 G3 — REST API contract для WebSocket ticket issuance.
 *
 * <p>Отдельный interface от {@link AuthApi}: другая категория endpoints,
 * требует Bearer access-JWT (не через cookie) и выдаёт short-lived WS-ticket.</p>
 */
@Tag(name = "Authentication", description = "JWT authentication endpoints")
@RequestMapping("/auth")
public interface WsTicketApi {

    @Operation(summary = "Issue WebSocket ticket",
            description = "Generate short-lived (30s, single-use) ticket for WebSocket handshake. "
                    + "Client uses it as `?ticket=<value>` query param. Replaces legacy "
                    + "`?token=<access_jwt>` pattern which leaked JWT into nginx/Gateway logs.")
    @ApiResponse(responseCode = "200", description = "Ticket issued")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @PostMapping("/ws-ticket")
    ResponseEntity<WsTicketResponse> issueTicket(Authentication authentication,
                                                 HttpServletRequest request);
}
