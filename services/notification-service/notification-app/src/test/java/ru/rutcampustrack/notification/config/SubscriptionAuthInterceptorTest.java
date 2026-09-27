package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubscriptionAuthInterceptorTest {

    private WsTicketClient ticketClient;
    private WsSessionBindingRegistry registry;
    private SubscriptionAuthInterceptor interceptor;
    private final AtomicInteger sessions = new AtomicInteger();

    @BeforeEach
    void setUp() {
        ticketClient = mock(WsTicketClient.class);
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);
        registry = new WsSessionBindingRegistry(ticketClient);
        interceptor = new SubscriptionAuthInterceptor(registry);
    }

    @Test
    void subscribeAllowsOnlyTheAuthenticatedUserDestination() {
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/user/7", identity(7, 42, false)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/user/8", identity(7, 42, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/user/7", Map.of("group_id", 42L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribePreservesOwnGroupAndHeadmanPermissions() {
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/group/42", identity(7, 42, false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> send(StompCommand.SUBSCRIBE, "/topic/group/42/headman", identity(7, 42, true)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/group/43", identity(7, 42, false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/group/42/headman", identity(7, 42, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void subscribeRejectsUnknownWildcardAndMalformedDestinations() {
        for (String destination : new String[]{
                "/queue/notifications",
                "/topic/group/*",
                "/topic/user/7/**",
                "/topic/user/0",
                "/topic/user/07",
                "/topic/group/9223372036854775808"
        }) {
            assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, destination, identity(7, 42, true)))
                    .as("destination %s must be rejected", destination)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void clientSendFramesAreRejected() {
        assertThatThrownBy(() -> send(StompCommand.SEND, "/topic/group/42", identity(7, 42, true)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ticketClient);
    }

    @Test
    void subscriptionRechecksLiveAuthorityBeforeAcceptance() {
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(false);

        assertThatThrownBy(() -> send(StompCommand.SUBSCRIBE, "/topic/group/42", identity(7, 42, false)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ticketClient, times(1)).admit(any(WsSessionAdmissionRequest.class));
    }

    @Test
    void connectRequiresIdentityFromTicketHandshakeAttributes() {
        assertThatThrownBy(() -> send(StompCommand.CONNECT, null, Map.of("user_id", 7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void send(StompCommand command, String destination, Map<String, Object> attributes) {
        String sessionId = "stomp-" + sessions.incrementAndGet();
        if (command != StompCommand.SEND && command != StompCommand.CONNECT) {
            WebSocketSession socket = mock(WebSocketSession.class);
            when(socket.getAttributes()).thenReturn(attributes);
            when(socket.isOpen()).thenReturn(true);
            registry.transportConnected(socket);
            interceptor.preSend(message(StompCommand.CONNECT, sessionId, null, attributes), null);
        }
        interceptor.preSend(message(command, sessionId, destination, attributes), null);
    }

    private static Message<byte[]> message(StompCommand command, String sessionId,
                                           String destination, Map<String, Object> attributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(attributes);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Map<String, Object> identity(long userId, long groupId, boolean headman) {
        WsSessionAdmissionRequest identity = new WsSessionAdmissionRequest(
                userId, UUID.randomUUID().toString(), 1, 1,
                headman ? "HEADMAN" : "STUDENT", "ACTIVE", groupId, headman, false);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE, identity);
        attributes.put(TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE, UUID.randomUUID().toString());
        return attributes;
    }
}
