package ru.rutcampustrack.notification.event;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.messaging.support.MessageBuilder;

import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;
import ru.rutcampustrack.notification.config.WsSessionAdmissionOutboundInterceptor;
import ru.rutcampustrack.notification.config.WsSessionBindingRegistry;
import ru.rutcampustrack.notification.config.WsTicketClient;

import java.util.*;

class ExportReadyEventConsumerTest {
    private Map<String, Object> event() {
        return new HashMap<>(
                Map.of(
                        "event_type",
                        "export.state.changed",
                        "source",
                        "academic-service",
                        "event_version",
                        1,
                        "event_id",
                        UUID.randomUUID().toString(),
                        "payload",
                        new HashMap<>(
                                Map.of(
                                        "job_id",
                                        UUID.randomUUID().toString(),
                                        "session_id",
                                        UUID.randomUUID().toString(),
                                        "user_id",
                                        7L,
                                        "state",
                                        "ready"))));
    }

    @Test
    void onlyPrivateMetadataAndServerGeneratedAuthorityHeadersLeaveTheConsumer() {
        var messaging = mock(SimpMessagingTemplate.class);
        var consumer = new ExportReadyEventConsumer(messaging);
        var event = event();
        consumer.onEvent(event);
        var payload = (Map<?, ?>) event.get("payload");
        verify(messaging)
                .convertAndSend(
                        eq("/topic/user/7"),
                        eq(
                                Map.of(
                                        "type",
                                        "export.state.changed",
                                        "jobId",
                                        payload.get("job_id"),
                                        "state",
                                        "ready")),
                        org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(
                                headers ->
                                        payload.get("job_id").equals(headers.get("rctExportJob"))
                                                && payload.get("session_id")
                                                        .equals(headers.get("rctExportSession"))));
    }

    @Test
    void wrongProducerUnknownVersionStateAndMalformedAudienceAreRejected() {
        var messaging = mock(SimpMessagingTemplate.class);
        var consumer = new ExportReadyEventConsumer(messaging);
        var source = event();
        source.put("source", "auth-service");
        assertThatThrownBy(() -> consumer.onEvent(source))
                .isInstanceOf(IllegalArgumentException.class);
        var version = event();
        version.put("event_version", 2);
        assertThatThrownBy(() -> consumer.onEvent(version))
                .isInstanceOf(IllegalArgumentException.class);
        var state = event();
        ((Map<String, Object>) state.get("payload")).put("state", "3");
        assertThatThrownBy(() -> consumer.onEvent(state))
                .isInstanceOf(IllegalArgumentException.class);
        var audience = event();
        ((Map<String, Object>) audience.get("payload")).put("user_id", 7.1);
        assertThatThrownBy(() -> consumer.onEvent(audience))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(messaging);
    }

    @Test
    void actualTemplateAndSimpleBrokerPreservePrivateHeadersAndRecheckEachDelivery() {
        var inbound = new ExecutorSubscribableChannel();
        var outbound = new ExecutorSubscribableChannel();
        var brokerChannel = new ExecutorSubscribableChannel();
        var registry = mock(WsSessionBindingRegistry.class);
        var admission = mock(WsTicketClient.class);
        var own =
                new WsSessionAdmissionRequest(
                        7,
                        UUID.randomUUID().toString(),
                        1,
                        1,
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false);
        var other =
                new WsSessionAdmissionRequest(
                        7,
                        UUID.randomUUID().toString(),
                        1,
                        1,
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false);
        when(registry.admit("own")).thenReturn(Optional.of(own));
        when(registry.admit("other")).thenReturn(Optional.of(other));
        outbound.addInterceptor(new WsSessionAdmissionOutboundInterceptor(registry, admission));
        List<Message<?>> messages = new ArrayList<>();
        outbound.subscribe(
                message -> {
                    if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders())
                            == SimpMessageType.MESSAGE) messages.add(message);
                });
        var broker =
                new SimpleBrokerMessageHandler(inbound, outbound, brokerChannel, List.of("/topic"));
        broker.start();
        try {
            for (String session : List.of("own", "other")) {
                var connect = SimpMessageHeaderAccessor.create(SimpMessageType.CONNECT);
                connect.setSessionId(session);
                inbound.send(
                        MessageBuilder.createMessage(new byte[0], connect.getMessageHeaders()));
                var subscribe = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
                subscribe.setSessionId(session);
                subscribe.setSubscriptionId("export");
                subscribe.setDestination("/topic/user/7");
                inbound.send(
                        MessageBuilder.createMessage(new byte[0], subscribe.getMessageHeaders()));
            }
            var event = event();
            var payload = (Map<String, Object>) event.get("payload");
            payload.put("session_id", own.sessionId());
            UUID job = UUID.fromString((String) payload.get("job_id"));
            when(admission.admitExport(job, own)).thenReturn(true, false);
            var consumer = new ExportReadyEventConsumer(new SimpMessagingTemplate(brokerChannel));
            consumer.onEvent(event);
            assertThat(messages).hasSize(1);
            assertThat(SimpMessageHeaderAccessor.getSessionId(messages.getFirst().getHeaders()))
                    .isEqualTo("own");
            assertThat(
                            messages.getFirst()
                                    .getHeaders()
                                    .get(WsSessionAdmissionOutboundInterceptor.EXPORT_JOB_HEADER))
                    .isEqualTo(job.toString());
            consumer.onEvent(event);
            assertThat(messages).hasSize(1);
            verify(admission, times(2)).admitExport(job, own);
            verify(admission, never()).admitExport(job, other);
        } finally {
            broker.stop();
        }
    }
}
