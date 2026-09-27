package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WsSessionAdmissionOutboundInterceptorTest {

    private WsTicketClient ticketClient;
    private WsSessionBindingRegistry registry;
    private WsSessionAdmissionOutboundInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ticketClient = mock(WsTicketClient.class);
        registry = new WsSessionBindingRegistry(ticketClient);
        interceptor = new WsSessionAdmissionOutboundInterceptor(registry);
    }

    @Test
    void outboundMessageRequiresFreshAdmissionForItsSocket() {
        bind("stomp-live");
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);
        Message<byte[]> message = outboundMessage("stomp-live");

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
        verify(ticketClient, times(1)).admit(any(WsSessionAdmissionRequest.class));
    }

    @Test
    void revokedAndUnknownSocketsNeverReceiveMessage() {
        bind("stomp-revoked");
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(false);
        Message<byte[]> revoked = outboundMessage("stomp-revoked");
        Message<byte[]> unknown = outboundMessage("stomp-unknown");

        assertThat(interceptor.preSend(revoked, null)).isNull();
        assertThat(interceptor.preSend(unknown, null)).isNull();

        verify(ticketClient, times(1)).admit(any(WsSessionAdmissionRequest.class));
        assertThat(registry.identity("stomp-revoked")).isEmpty();
    }

    private void bind(String sessionId) {
        Map<String, Object> attributes = Map.of(
                TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE,
                new WsSessionAdmissionRequest(7, UUID.randomUUID().toString(), 2, 3,
                        "STUDENT", "ACTIVE", 42L, false, false),
                TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE,
                UUID.randomUUID().toString());
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        registry.transportConnected(socket);
        assertThat(registry.bindStompSession(sessionId, attributes)).isTrue();
    }

    private static Message<byte[]> outboundMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/user/7");
        return MessageBuilder.createMessage(new byte[]{1}, accessor.getMessageHeaders());
    }
}
