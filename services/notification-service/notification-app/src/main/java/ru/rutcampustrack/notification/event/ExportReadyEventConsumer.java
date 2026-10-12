package ru.rutcampustrack.notification.event;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.config.WsSessionAdmissionOutboundInterceptor;

/** Private metadata hints. Outbound admission and authenticated REST own all disclosure. */
@Component
public class ExportReadyEventConsumer {
    private final SimpMessagingTemplate messaging;
    public ExportReadyEventConsumer(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    @RabbitListener(queues = "notification-web.exports", containerFactory = "notificationHistoryRabbitListenerContainerFactory")
    public void onEvent(Map<String, Object> event) {
        if (!"export.state.changed".equals(event.get("event_type"))) return;
        if (!"auth-service".equals(event.get("source")) || !Integer.valueOf(1).equals(event.get("event_version"))
                || !(event.get("payload") instanceof Map<?, ?> payload) || !payload.keySet().equals(Set.of("job_id", "user_id", "session_id", "state"))) {
            throw new IllegalArgumentException("Invalid export event");
        }
        UUID jobId = uuid(payload.get("job_id"));
        UUID sessionId = uuid(payload.get("session_id"));
        uuid(event.get("event_id"));
        Object rawUser = payload.get("user_id");
        if (!(rawUser instanceof Number) || !rawUser.toString().matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid export audience");
        long userId = Long.parseLong(rawUser.toString());
        if (!(payload.get("state") instanceof String state) || !Set.of("queued", "preparing", "ready", "error").contains(state)) throw new IllegalArgumentException("Invalid export state");
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setHeader(WsSessionAdmissionOutboundInterceptor.EXPORT_JOB_HEADER, jobId.toString());
        headers.setHeader(WsSessionAdmissionOutboundInterceptor.EXPORT_SESSION_HEADER, sessionId.toString());
        messaging.convertAndSend("/topic/user/" + userId, Map.of("type", "export.state.changed", "jobId", jobId.toString(), "state", state), headers.getMessageHeaders());
    }

    private static UUID uuid(Object raw) {
        if (!(raw instanceof String value)) throw new IllegalArgumentException("Invalid export identity");
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("Invalid export identity");
        return id;
    }

    /** Separate queue avoids competing with existing notification/history listeners. Duplicate hints are harmless. */
    @Configuration
    public static class QueueConfiguration {
        @Bean public Queue notificationExportQueue() {
            return QueueBuilder.durable("notification-web.exports").withArgument("x-dead-letter-exchange", "rut-uit.events.dlq")
                    .withArgument("x-dead-letter-routing-key", "notification-web.exports.dlq").build();
        }
        @Bean public Queue notificationExportDlq() { return QueueBuilder.durable("notification-web.exports.dlq").build(); }
        @Bean public Binding notificationExportBinding(FanoutExchange notificationWebEventsExchange, Queue notificationExportQueue) {
            return BindingBuilder.bind(notificationExportQueue).to(notificationWebEventsExchange);
        }
        @Bean public Binding notificationExportDlqBinding(DirectExchange notificationWebDlqExchange, Queue notificationExportDlq) {
            return BindingBuilder.bind(notificationExportDlq).to(notificationWebDlqExchange).with("notification-web.exports.dlq");
        }
    }
}
