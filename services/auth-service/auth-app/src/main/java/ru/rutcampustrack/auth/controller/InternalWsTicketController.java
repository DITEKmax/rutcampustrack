package ru.rutcampustrack.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalWsTicketApi;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.WsTicketService;
import ru.rutcampustrack.auth.session.AuthSessionException;

import java.util.Objects;
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
    private final AuthService authService;

    public InternalWsTicketController(WsTicketService wsTicketService, AuthService authService) {
        this.wsTicketService = Objects.requireNonNull(wsTicketService, "wsTicketService");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    @Override
    public ResponseEntity<ConsumeWsTicketResponse> consume(ConsumeWsTicketRequest request) {
        Optional<WsTicketService.TicketClaims> ticket = wsTicketService.consume(request.ticket());
        if (ticket.isEmpty()) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }
        WsTicketService.TicketClaims claims = ticket.get();
        SessionPrincipal principal = claims.principal();
        try {
            authService.admit(principal);
        } catch (AuthSessionException exception) {
            if (exception.code() == AuthSessionException.Code.AUTHORITY_UNAVAILABLE) {
                throw exception;
            }
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok(toResponse(principal, claims.expiresAt()));
    }

    private static ConsumeWsTicketResponse toResponse(SessionPrincipal principal,
                                                     java.time.Instant expiresAt) {
        return new ConsumeWsTicketResponse(principal.userId(), principal.sessionId().toString(),
                principal.sessionVersion(), principal.rolesVersion(), principal.selectedRole().name(),
                principal.selectedStatus().name(), principal.groupId(), principal.headman(),
                principal.readOnly(), expiresAt);
    }
}
