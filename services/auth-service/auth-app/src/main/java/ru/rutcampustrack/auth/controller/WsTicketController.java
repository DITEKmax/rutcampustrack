package ru.rutcampustrack.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.WsTicketApi;
import ru.rutcampustrack.auth.dto.WsTicketResponse;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.WsTicketService;
import ru.rutcampustrack.auth.session.AuthSessionException;

/**
 * M03b Группа 3: issues short-lived tickets для WebSocket handshake.
 * Защищён access-JWT (Spring Security default + {@code JwtAuthenticationFilter}).
 * Никогда не отдаётся анонимно — {@code Authentication} обязателен.
 *
 * <p>Stores the principal created by the session-aware JWT filter and checks that
 * identity against current authority before issuing a socket capability.</p>
 */
@RestController
public class WsTicketController implements WsTicketApi {

    private final WsTicketService wsTicketService;
    private final AuthService authService;

    public WsTicketController(WsTicketService wsTicketService, AuthService authService) {
        this.wsTicketService = wsTicketService;
        this.authService = authService;
    }

    @Override
    public ResponseEntity<WsTicketResponse> issueTicket(Authentication authentication,
                                                        HttpServletRequest request) {
        SessionPrincipal principal = principal(authentication);
        if (principal.isBootstrap()) {
            throw new AuthSessionException(AuthSessionException.Code.BOOTSTRAP_SCOPE_DENIED);
        }
        authService.admit(principal);

        WsTicketService.Issued issued = wsTicketService.issue(principal);
        return ResponseEntity.ok(new WsTicketResponse(issued.ticket(), issued.expiresAt()));
    }

    private static SessionPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new InvalidCredentialsException();
        }
        return principal;
    }
}
