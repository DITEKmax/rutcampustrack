package ru.rutcampustrack.auth.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.service.WsTicketService;

import java.time.Instant;
import java.util.Optional;

/**
 * M03b Группа 3: internal endpoint для notification-web — atomic consume
 * single-use WebSocket ticket. Защищён {@code InternalIssuerSecretFilter}
 * (тот же shared secret, что {@code /internal/issue-internal-jwt}).
 *
 * Notification-web вызывает этот endpoint в {@code TicketHandshakeInterceptor}
 * (Группа 4) после приёма {@code ?ticket=<uuid>} query-param из upgrade
 * request'а WebSocket.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Internal", description = "Gateway/service-to-service endpoints (shared-secret guarded)")
public class InternalWsTicketController {

    private final WsTicketService wsTicketService;

    public InternalWsTicketController(WsTicketService wsTicketService) {
        this.wsTicketService = wsTicketService;
    }

    @Operation(summary = "Consume WebSocket ticket",
               description = "Atomic GET+DEL for single-use ticket. Returns {userId, role} "
                       + "if ticket was valid + not yet consumed, otherwise 404.")
    @ApiResponse(responseCode = "200", description = "Ticket consumed, identity returned")
    @ApiResponse(responseCode = "404", description = "Ticket not found or already consumed/expired")
    @PostMapping("/consume-ws-ticket")
    public ResponseEntity<ConsumeResponse> consume(@RequestBody ConsumeRequest request) {
        Optional<WsTicketService.TicketClaims> claims = wsTicketService.consume(request.ticket());
        return claims
                .<ResponseEntity<ConsumeResponse>>map(c -> ResponseEntity.ok(
                        new ConsumeResponse(c.userId(), c.role(), c.expiresAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record ConsumeRequest(@NotBlank String ticket) {}

    public record ConsumeResponse(
            @JsonProperty("user_id") long userId,
            String role,
            @JsonProperty("expires_at") Instant expiresAt
    ) {}
}
