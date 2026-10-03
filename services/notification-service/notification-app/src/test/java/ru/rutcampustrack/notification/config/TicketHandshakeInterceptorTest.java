package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
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

    private static final String TICKET = "12345678-1234-1234-1234-123456789abc";
    private HttpHeaders headers;
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
        headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        response = mock(ServerHttpResponse.class);
        handler = mock(WebSocketHandler.class);
    }

    @Test
    void beforeHandshake_validTicketStoresOnlyAdmittedSessionIdentity() {
        ConsumeWsTicketResponse ticketIdentity = identity(42L, "STUDENT", 7L, false);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=12345678-1234-1234-1234-123456789abc"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.of(ticketIdentity));
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
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=12345678-1234-1234-1234-123456789abc"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.empty());

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshake_sockjsStylePath_stillFindsTicket() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws/123/abc/websocket?ticket=12345678-1234-1234-1234-123456789abc"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.of(
                identity(10L, "TEACHER", null, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isTrue();
        assertThat(attrs).containsEntry("role", "TEACHER");
    }

    @Test
    void beforeHandshake_ticketWithOtherParams_picksTicketOnly() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?foo=bar&ticket=12345678-1234-1234-1234-123456789abc&baz=qux"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.of(
                identity(1L, "STUDENT", 2L, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        boolean result = interceptor.beforeHandshake(request, response, handler, new HashMap<>());

        assertThat(result).isTrue();
        verify(ticketClient).consume(TICKET);
    }

    @Test
    void beforeHandshake_revokedIdentityRejectsAfterTicketConsume() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=12345678-1234-1234-1234-123456789abc"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.of(identity(8L, "STUDENT", 9L, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(false);

        Map<String, Object> attrs = new HashMap<>();
        boolean result = interceptor.beforeHandshake(request, response, handler, attrs);

        assertThat(result).isFalse();
        assertThat(attrs).doesNotContainKey(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE);
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beforeHandshake_headerTicketUsesSameConsumeAndLiveAdmission() {
        headers.set(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws/123/abc/websocket"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.of(identity(42L, "STUDENT", 7L, false)));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        assertThat(interceptor.beforeHandshake(request, response, handler, new HashMap<>())).isTrue();
        verify(ticketClient, times(1)).consume(TICKET);
        verify(ticketClient, times(1)).admit(any(WsSessionAdmissionRequest.class));
    }

    @Test
    void beforeHandshake_duplicateHeaderRejectedBeforeConsume() {
        headers.add(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        headers.add(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws"));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_headerAndSameQueryRejectedBeforeConsume() {
        headers.set(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=" + TICKET));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_headerAndDifferentQueryRejectedBeforeConsume() {
        headers.set(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=" + UUID.randomUUID()));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_nonCanonicalHeaderRejectedBeforeConsume() {
        headers.set(TicketHandshakeInterceptor.TICKET_HEADER, TICKET.toUpperCase());
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws"));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_duplicateQueryIncludingEncodedNameRejectedBeforeConsume() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=" + TICKET + "&%74icket=" + TICKET));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_malformedQueryTicketRejectedBeforeConsume() {
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws?ticket=not-a-ticket"));
        assertRejectedBeforeConsume();
    }

    @Test
    void beforeHandshake_consumedHeaderTicketRejectedWithoutAdmission() {
        headers.set(TicketHandshakeInterceptor.TICKET_HEADER, TICKET);
        when(request.getURI()).thenReturn(URI.create("https://ruttrack.site/ws"));
        when(ticketClient.consume(TICKET)).thenReturn(Optional.empty());
        assertThat(interceptor.beforeHandshake(request, response, handler, new HashMap<>())).isFalse();
        verify(ticketClient, times(1)).consume(TICKET);
        verify(ticketClient, never()).admit(any());
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    private void assertRejectedBeforeConsume() {
        assertThat(interceptor.beforeHandshake(request, response, handler, new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(ticketClient);
    }

    private static ConsumeWsTicketResponse identity(long userId, String role, Long groupId, boolean headman) {
        return new ConsumeWsTicketResponse(userId, UUID.randomUUID().toString(), 1, 1,
                role, "ACTIVE", groupId, headman, false, Instant.now());
    }
}
