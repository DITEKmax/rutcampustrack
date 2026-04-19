package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TicketHandshakeInterceptorTest {

    private WsTicketClient ticketClient;
    private TicketHandshakeInterceptor interceptor;
    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private WebSocketHandler handler;

    @BeforeEach
    void setUp() {
        ticketClient = mock(WsTicketClient.class);
        interceptor = new TicketHandshakeInterceptor(ticketClient);
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        handler = mock(WebSocketHandler.class);
    }

    @Test
    void beforeHandshake_validTicket_populatesSessionAttributes() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=abc-uuid"));
        when(ticketClient.consume("abc-uuid")).thenReturn(Optional.of(
                new WsTicketClient.TicketIdentity(42L, "STUDENT", 7L, true, Instant.now())));

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry("user_id", 42L);
        assertThat(attrs).containsEntry("group_id", 7L);
        assertThat(attrs).containsEntry("role", "STUDENT");
        assertThat(attrs).containsEntry("is_headman", true);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    void beforeHandshake_missingTicket_rejectsWith401() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws"));

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(ticketClient);
    }

    @Test
    void beforeHandshake_invalidOrConsumedTicket_rejectsWith401() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=bad"));
        when(ticketClient.consume("bad")).thenReturn(Optional.empty());

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshake_sockjsStylePath_stillFindsTicket() {
        // SockJS session-keyed path: /ws/123/abc/websocket?ticket=<uuid>
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws/123/abc/websocket?ticket=tkt-42"));
        when(ticketClient.consume("tkt-42")).thenReturn(Optional.of(
                new WsTicketClient.TicketIdentity(10L, "TEACHER", 0L, false, Instant.now())));

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry("role", "TEACHER");
    }

    @Test
    void beforeHandshake_ticketWithOtherParams_picksTicketOnly() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?foo=bar&ticket=xyz&baz=qux"));
        when(ticketClient.consume("xyz")).thenReturn(Optional.of(
                new WsTicketClient.TicketIdentity(1L, "STUDENT", 2L, false, Instant.now())));

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isTrue();
        verify(ticketClient).consume("xyz");
    }
}
