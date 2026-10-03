package ru.rutcampustrack.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * M03b Группа 4: replaces {@link JwtHandshakeInterceptor}.
 *
 * <p>Reads a canonical {@code X-RCT-WS-Ticket} header or legacy
 * {@code ?ticket=<uuid>} query param, exchanges it against
 * auth-service {@code /internal/consume-ws-ticket} atomically (single-use),
 * stores resulting identity in STOMP session attributes. Rejects
 * handshake if ticket missing / invalid / already consumed / expired.</p>
 *
 * <p>Session attributes layout matches legacy {@code JwtHandshakeInterceptor}:
 * {@code user_id}, {@code group_id}, {@code role}, {@code is_headman}.
 * SubscriptionAuthInterceptor продолжает работать без изменений.</p>
 */
@Component
@Slf4j
public class TicketHandshakeInterceptor implements HandshakeInterceptor {

    static final String SESSION_IDENTITY_ATTRIBUTE = "ws_session_identity";
    static final String TRANSPORT_BINDING_ID_ATTRIBUTE = "ws_transport_binding_id";
    static final String TICKET_HEADER = "X-RCT-WS-Ticket";

    private final WsTicketClient ticketClient;

    public TicketHandshakeInterceptor(WsTicketClient ticketClient) {
        this.ticketClient = ticketClient;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                    ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        String ticket = extractTicket(request);
        if (ticket == null) {
            log.debug("WS handshake rejected — missing or ambiguous ticket");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<ConsumeWsTicketResponse> consumed = ticketClient.consume(ticket);
        if (consumed.isEmpty()) {
            log.debug("WS handshake rejected — invalid/consumed ticket");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        WsSessionAdmissionRequest identity;
        try {
            identity = consumed.get().admissionRequest();
        } catch (RuntimeException exception) {
            log.debug("WS handshake rejected — invalid session identity");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (!ticketClient.admit(identity)) {
            log.debug("WS handshake rejected — live session admission failed");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(SESSION_IDENTITY_ATTRIBUTE, identity);
        attributes.put(TRANSPORT_BINDING_ID_ATTRIBUTE, UUID.randomUUID().toString());
        attributes.put("user_id", identity.userId());
        if (identity.groupId() != null) {
            attributes.put("group_id", identity.groupId());
        }
        attributes.put("role", identity.role());
        attributes.put("is_headman", identity.isHeadman());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler handler, Exception exception) {
        // no-op
    }

    private static String extractTicket(ServerHttpRequest request) {
        List<String> headerTickets = request.getHeaders().get(TICKET_HEADER);
        String queryTicket = null;
        boolean queryTicketPresent = false;
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery != null) {
            for (String param : rawQuery.split("&")) {
                String[] pair = param.split("=", 2);
                try {
                    if (!"ticket".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) continue;
                    if (queryTicketPresent) return null;
                    queryTicketPresent = true;
                    queryTicket = pair.length == 2
                            ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : null;
                } catch (IllegalArgumentException malformedEncoding) {
                    return null;
                }
            }
        }
        if (headerTickets != null) {
            if (headerTickets.size() != 1 || queryTicketPresent) return null;
            return canonicalTicket(headerTickets.getFirst());
        }
        return canonicalTicket(queryTicket);
    }

    private static String canonicalTicket(String ticket) {
        if (ticket == null || ticket.length() != 36) return null;
        try {
            return UUID.fromString(ticket).toString().equals(ticket) ? ticket : null;
        } catch (IllegalArgumentException invalidUuid) {
            return null;
        }
    }
}
