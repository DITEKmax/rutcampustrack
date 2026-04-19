package ru.rutcampustrack.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.dto.WsTicketResponse;
import ru.rutcampustrack.auth.service.WsTicketService;

/**
 * M03b Группа 3: issues short-lived tickets для WebSocket handshake.
 * Защищён access-JWT (Spring Security default + {@code JwtAuthenticationFilter}).
 * Никогда не отдаётся анонимно — {@code Authentication} обязателен.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class WsTicketController {

    private final WsTicketService wsTicketService;

    public WsTicketController(WsTicketService wsTicketService) {
        this.wsTicketService = wsTicketService;
    }

    @Operation(summary = "Issue WebSocket ticket",
               description = "Generate short-lived (30s, single-use) ticket for WebSocket handshake. "
                       + "Client uses it as `?ticket=<value>` query param. Replaces legacy "
                       + "`?token=<access_jwt>` pattern which leaked JWT into nginx/Gateway logs.")
    @ApiResponse(responseCode = "200", description = "Ticket issued")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @PostMapping("/ws-ticket")
    public ResponseEntity<WsTicketResponse> issueTicket(Authentication authentication) {
        long userId = Long.parseLong(authentication.getName());
        String role = extractRole(authentication);
        WsTicketService.Issued issued = wsTicketService.issue(userId, role);
        return ResponseEntity.ok(new WsTicketResponse(issued.ticket(), issued.expiresAt()));
    }

    private static String extractRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElse("UNKNOWN");
    }
}
