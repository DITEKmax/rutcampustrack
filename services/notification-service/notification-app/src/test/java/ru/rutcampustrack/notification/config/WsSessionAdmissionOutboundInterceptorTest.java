package ru.rutcampustrack.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.WebSocketSession;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

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
        bind(sessionId, "STUDENT");
    }

    private void bind(String sessionId, String role) {
        Map<String, Object> attributes =
                Map.of(
                        TicketHandshakeInterceptor.SESSION_IDENTITY_ATTRIBUTE,
                        new WsSessionAdmissionRequest(
                                7,
                                UUID.randomUUID().toString(),
                                2,
                                3,
                                role,
                                "ACTIVE",
                                "ADMIN".equals(role) ? null : 42L,
                                false,
                                false),
                        TicketHandshakeInterceptor.TRANSPORT_BINDING_ID_ATTRIBUTE,
                        UUID.randomUUID().toString());
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(attributes);
        when(socket.isOpen()).thenReturn(true);
        registry.transportConnected(socket);
        assertThat(registry.bindStompSession(sessionId, attributes)).isTrue();
    }

    @Test
    void dashboardDeliveryRechecksCapabilityForEveryMessageAndFailsClosedAfterRevocation() {
        bind("stomp-admin", "ADMIN");
        when(ticketClient.admitDashboard(any())).thenReturn(true, false);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headers.setSessionId("stomp-admin");
        headers.setDestination("/topic/admin/dashboard");
        Message<byte[]> message =
                MessageBuilder.createMessage(new byte[] {1}, headers.getMessageHeaders());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
        assertThat(interceptor.preSend(message, null)).isNull();
        assertThat(interceptor.preSend(message, null)).isNull();
        assertThat(registry.identity("stomp-admin")).isEmpty();
        verify(ticketClient, times(2)).admitDashboard(any());
        verify(ticketClient, never()).admit(any());
    }

    @Test
    void exportDeliveryRequiresTheExactSocketSessionAudienceAndFreshJobAdmissionForEveryHint() {
        bind("stomp-export", "ADMIN");
        interceptor = new WsSessionAdmissionOutboundInterceptor(registry, ticketClient);
        var identity = registry.identity("stomp-export").orElseThrow();
        UUID job = UUID.randomUUID();
        when(ticketClient.admit(identity)).thenReturn(true);
        when(ticketClient.admitExport(job, identity)).thenReturn(true, false);
        Message<byte[]> message =
                exportMessage(
                        "stomp-export", "/topic/user/7", job.toString(), identity.sessionId());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
        assertThat(interceptor.preSend(message, null)).isNull();
        verify(ticketClient, times(2)).admitExport(job, identity);
        assertThat(registry.identity("stomp-export")).isPresent();
    }

    @Test
    void forgedSessionAudiencePartialOrMalformedExportHeadersNeverReachJobAdmission() {
        bind("stomp-export", "ADMIN");
        interceptor = new WsSessionAdmissionOutboundInterceptor(registry, ticketClient);
        var identity = registry.identity("stomp-export").orElseThrow();
        UUID job = UUID.randomUUID();
        when(ticketClient.admit(identity)).thenReturn(true);
        assertThat(
                        interceptor.preSend(
                                exportMessage(
                                        "stomp-export",
                                        "/topic/user/7",
                                        job.toString(),
                                        UUID.randomUUID().toString()),
                                null))
                .isNull();
        assertThat(
                        interceptor.preSend(
                                exportMessage(
                                        "stomp-export",
                                        "/topic/user/8",
                                        job.toString(),
                                        identity.sessionId()),
                                null))
                .isNull();
        assertThat(
                        interceptor.preSend(
                                exportMessage(
                                        "stomp-export",
                                        "/topic/user/7",
                                        "invalid",
                                        identity.sessionId()),
                                null))
                .isNull();
        assertThat(
                        interceptor.preSend(
                                exportMessage(
                                        "stomp-export", "/topic/user/7", job.toString(), null),
                                null))
                .isNull();
        verify(ticketClient, never()).admitExport(any(), any());
    }

    private static Message<byte[]> exportMessage(
            String socket, String destination, String job, String session) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.MESSAGE);
        headers.setSessionId(socket);
        headers.setDestination(destination);
        headers.setHeader(WsSessionAdmissionOutboundInterceptor.EXPORT_JOB_HEADER, job);
        if (session != null)
            headers.setHeader(WsSessionAdmissionOutboundInterceptor.EXPORT_SESSION_HEADER, session);
        return MessageBuilder.createMessage(new byte[] {1}, headers.getMessageHeaders());
    }

    private static Message<byte[]> outboundMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/topic/user/7");
        return MessageBuilder.createMessage(new byte[] {1}, accessor.getMessageHeaders());
    }
}
