package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    void beforeHandshake_validTicketStoresOnlyAdmittedSessionIdentity() {
        ConsumeWsTicketResponse ticketIdentity = identity(42L, "STUDENT", 7L, false);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=abc-uuid"));
        when(ticketClient.consume("abc-uuid")).thenReturn(Optional.of(ticketIdentity));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry("user_id", 42L);
        assertThat(attrs).containsEntry("group_id", 7L);
        assertThat(attrs).containsEntry("role", "STUDENT");
        assertThat(attrs).containsEntry("is_headman", false);
        assertThat(attrs.get(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE))
                .isEqualTo(ticketIdentity.admissionRequest());
        assertThat(UUID.fromString((String) attrs.get(
                TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE))).isNotNull();
        assertThat(attrs).doesNotContainKey("ticket");
        verify(ticketClient).admit(any(WsSessionAdmissionRequest.class));
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
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws/123/abc/websocket?ticket=tkt-42"));
        when(ticketClient.consume("tkt-42")).thenReturn(Optional.of(
                identity(10L, "TEACHER", null, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry("role", "TEACHER");
    }

    @Test
    void beforeHandshake_ticketWithOtherParams_picksTicketOnly() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?foo=bar&ticket=xyz&baz=qux"));
        when(ticketClient.consume("xyz")).thenReturn(Optional.of(
                identity(1L, "STUDENT", 2L, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isTrue();
        verify(ticketClient).consume("xyz");
    }

    @Test
    void beforeHandshake_revokedIdentityRejectsAfterTicketConsume() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=tkt"));
        when(ticketClient.consume("tkt")).thenReturn(Optional.of(identity(8L, "STUDENT", 9L, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(false);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isFalse();
        assertThat(attrs).doesNotContainKey(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private static ConsumeWsTicketResponse identity(long userId, String role, Long groupId, boolean headman) {
        return new ConsumeWsTicketResponse(userId, UUID.randomUUID().toString(), 1, 1,
                role, "ACTIVE", groupId, headman, false, Instant.now());
    }
}
