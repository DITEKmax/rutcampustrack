package ru.rutcampustrack.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalWsTicketApi;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.service.WsTicketService;

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
public class InternalWsTicketController implements InternalWsTicketApi {

    private final WsTicketService wsTicketService;

    public InternalWsTicketController(WsTicketService wsTicketService) {
        this.wsTicketService = wsTicketService;
    }

    @Override
    public ResponseEntity<ConsumeWsTicketResponse> consume(ConsumeWsTicketRequest request) {
        Optional<WsTicketService.TicketClaims> claims = wsTicketService.consume(request.ticket());
        return claims
                .<ResponseEntity<ConsumeWsTicketResponse>>map(c -> ResponseEntity.ok(
                        new ConsumeWsTicketResponse(c.userId(), c.role(), c.groupId(),
                                c.isHeadman(), c.expiresAt())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
