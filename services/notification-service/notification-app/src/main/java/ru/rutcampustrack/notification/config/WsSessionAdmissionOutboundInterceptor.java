package ru.rutcampustrack.notification.config;

import org.springframework.messaging.Message;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/** Drops each protected broker MESSAGE unless its socket still has live auth authority. */
@Component
public final class WsSessionAdmissionOutboundInterceptor implements ChannelInterceptor {

    public static final String EXPORT_JOB_HEADER = "rctExportJob";
    public static final String EXPORT_SESSION_HEADER = "rctExportSession";
    private final WsSessionBindingRegistry registry;
    private final WsTicketClient ticketClient;

    public WsSessionAdmissionOutboundInterceptor(WsSessionBindingRegistry registry) {
        this(registry, null);
    }

    @Autowired
    public WsSessionAdmissionOutboundInterceptor(WsSessionBindingRegistry registry, WsTicketClient ticketClient) {
        this.registry = registry; this.ticketClient = ticketClient;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        Object jobHeader = message.getHeaders().get(EXPORT_JOB_HEADER);
        Object authSession = message.getHeaders().get(EXPORT_SESSION_HEADER);
        if (jobHeader != null || authSession != null) {
            if (!(jobHeader instanceof String jobText) || !(authSession instanceof String sessionText) || ticketClient == null) return null;
            final UUID jobId;
            try { jobId = UUID.fromString(jobText); if (!jobId.toString().equals(jobText)) return null; }
            catch (IllegalArgumentException invalid) { return null; }
            var identity = registry.admit(sessionId);
            if (identity.isEmpty() || !sessionText.equals(identity.get().sessionId())
                    || !("/topic/user/" + identity.get().userId()).equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))) return null;
            return ticketClient.admitExport(jobId, identity.get()) ? message : null;
        }
        if (SubscriptionAuthInterceptor.DASHBOARD_TOPIC.equals(
                SimpMessageHeaderAccessor.getDestination(message.getHeaders()))) {
            return registry.admitDashboard(sessionId).isPresent() ? message : null;
        }
        return registry.admit(sessionId).isPresent() ? message : null;
    }
}
