package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;

/**
 * M12 G3 — Internal endpoint для notification-web: atomic consume single-use
 * WebSocket ticket. Защищён {@code InternalIssuerSecretFilter} (shared secret,
 * тот же что для {@link InternalIssuerApi#issue}).
 *
 * <p>Отделён от {@link InternalIssuerApi} — разные domain boundaries
 * (M12 D3: раздельные interfaces).</p>
 *
 * <p>{@code @Hidden} — endpoint не появляется в public /v3/api-docs.</p>
 */
@Hidden
@Tag(name = "Internal", description = "Gateway/service-to-service endpoints (shared-secret guarded)")
@RequestMapping("/internal")
public interface InternalWsTicketApi {

    @Operation(summary = "Consume WebSocket ticket",
            description = "Atomic GET+DEL for single-use ticket. Returns {userId, role} "
                    + "if ticket was valid + not yet consumed, otherwise 404.")
    @ApiResponse(responseCode = "200", description = "Ticket consumed, identity returned")
    @ApiResponse(responseCode = "404", description = "Ticket not found or already consumed/expired")
    @PostMapping("/consume-ws-ticket")
    ResponseEntity<ConsumeWsTicketResponse> consume(@RequestBody ConsumeWsTicketRequest request);
}
