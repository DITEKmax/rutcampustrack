package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WsSessionBindingRegistryTest {

    @Test
    void unboundRawTransportsAreBoundedAndCapacityReturnsOnDisconnect() throws Exception {
        WsSessionBindingRegistry registry = new WsSessionBindingRegistry(mock(WsTicketClient.class), 1, 1);
        WebSocketSession first = socket(trustedAttributes());
        WebSocketSession second = socket(trustedAttributes());

        registry.transportConnected(first);
        registry.transportConnected(second);
        verify(second).close(new CloseStatus(1013, "Session capacity reached"));

        registry.transportDisconnected(first);
        registry.transportConnected(second);
        verify(second, times(1)).close(any(CloseStatus.class));
    }

    @Test
    void rejectedAdmissionEvictsBindingClosesTransportAndDoesNotRetry() throws Exception {
        WsTicketClient ticketClient = mock(WsTicketClient.class);
        when(ticketClient.admit(any())).thenReturn(false);
        WsSessionBindingRegistry registry = new WsSessionBindingRegistry(ticketClient);
        Map<String, Object> attributes = trustedAttributes();
        WebSocketSession socket = socket(attributes);

        registry.transportConnected(socket);
        assertThat(registry.bindStompSession("stomp-1", attributes)).isTrue();
        assertThat(registry.admit("stomp-1")).isEmpty();
        assertThat(registry.admit("stomp-1")).isEmpty();

        verify(ticketClient, times(1)).admit(any(WsSessionAdmissionRequest.class));
        verify(socket, times(1)).close(any(CloseStatus.class));
        assertThat(registry.identity("stomp-1")).isEmpty();
    }

    @Test
    void disconnectReleasesBindingAndClosesSocket() throws Exception {
        WsTicketClient ticketClient = mock(WsTicketClient.class);
        WsSessionBindingRegistry registry = new WsSessionBindingRegistry(ticketClient);
        Map<String, Object> attributes = trustedAttributes();
        WebSocketSession socket = socket(attributes);

        registry.transportConnected(socket);
        assertThat(registry.bindStompSession("stomp-2", attributes)).isTrue();
        registry.disconnectStompSession("stomp-2");

        assertThat(registry.identity("stomp-2")).isEmpty();
        verify(socket).close(CloseStatus.NORMAL);
        verifyNoInteractions(ticketClient);
    }

    private static Map<String, Object> trustedAttributes() {
        return Map.of(
                TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE,
                new WsSessionAdmissionRequest(7, UUID.randomUUID().toString(), 2, 3,
                        "STUDENT", "ACTIVE", 42L, false, false),
                TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE,
                UUID.randomUUID().toString());
    }

    private static WebSocketSession socket(Map<String, Object> attributes) {
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        return socket;
    }
}
