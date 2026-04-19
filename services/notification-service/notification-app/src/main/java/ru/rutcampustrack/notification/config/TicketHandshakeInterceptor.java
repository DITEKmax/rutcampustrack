package ru.rutcampustrack.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

/**
 * M03b Группа 4: replaces {@link JwtHandshakeInterceptor}.
 *
 * <p>Reads {@code ?ticket=<uuid>} query param, exchanges it against
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
            log.debug("WS handshake rejected — missing ?ticket=");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<WsTicketClient.TicketIdentity> identity = ticketClient.consume(ticket);
        if (identity.isEmpty()) {
            log.debug("WS handshake rejected — invalid/consumed ticket");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        WsTicketClient.TicketIdentity id = identity.get();
        attributes.put("user_id", id.userId());
        attributes.put("group_id", id.groupId());
        attributes.put("role", id.role());
        attributes.put("is_headman", id.isHeadman());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler handler, Exception exception) {
        // no-op
    }

    private static String extractTicket(ServerHttpRequest request) {
        String rawQuery = request.getURI().getRawQuery();
        if (rawQuery == null) return null;
        for (String param : rawQuery.split("&")) {
            if (param.startsWith("ticket=")) {
                return param.substring(7);
            }
        }
        return null;
    }
}
