package ru.rutcampustrack.notification.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/** Drops each protected broker MESSAGE unless its socket still has live auth authority. */
@Component
public final class WsSessionAdmissionOutboundInterceptor implements ChannelInterceptor {

    private final WsSessionBindingRegistry registry;

    public WsSessionAdmissionOutboundInterceptor(WsSessionBindingRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        return registry.admit(sessionId).isPresent() ? message : null;
    }
}
