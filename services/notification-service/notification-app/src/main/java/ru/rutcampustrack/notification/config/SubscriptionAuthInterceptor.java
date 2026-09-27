package ru.rutcampustrack.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates client subscriptions against the live identity established by the ticket handshake.
 *
 * <p>Only supported group, headman, and personal destinations are allowed. Client SEND frames
 * are rejected because this service only publishes broker notifications from server consumers.
 */
@Component
@Slf4j
public class SubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/group/([1-9][0-9]*)(/headman)?$");
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/user/([1-9][0-9]*)$");

    private final WsSessionBindingRegistry registry;

    public SubscriptionAuthInterceptor(WsSessionBindingRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            log.warn("Client inbound message rejected — missing STOMP headers");
            throw new IllegalArgumentException("Unsupported client message");
        }

        if (accessor.getCommand() == StompCommand.SEND) {
            log.warn("Client STOMP SEND frame rejected");
            throw new IllegalArgumentException("Client messages are not supported");
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            if (!registry.bindStompSession(accessor.getSessionId(), accessor.getSessionAttributes())) {
                log.warn("STOMP CONNECT rejected — missing trusted WebSocket session binding");
                throw new IllegalArgumentException("Unauthorized WebSocket session");
            }
            return message;
        }

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        var identity = registry.identity(accessor.getSessionId());
        if (identity.isEmpty()) {
            log.warn("SUBSCRIBE rejected — no trusted binding for destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription");
        }

        Matcher userMatcher = USER_TOPIC.matcher(destination == null ? "" : destination);
        if (userMatcher.matches()) {
            Long requestedUserId = parsePositiveId(userMatcher.group(1));
            if (requestedUserId == null || identity.get().userId() != requestedUserId) {
                log.warn("SUBSCRIBE rejected — user identity does not match destination {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription");
            }
        } else {
            Matcher groupMatcher = GROUP_TOPIC.matcher(destination == null ? "" : destination);
            if (!groupMatcher.matches()) {
                log.warn("SUBSCRIBE rejected — unsupported destination {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription");
            }

            Long requestedGroupId = parsePositiveId(groupMatcher.group(1));
            Long sessionGroupId = identity.get().groupId();
            if (requestedGroupId == null || sessionGroupId == null
                    || !sessionGroupId.equals(requestedGroupId)) {
                log.warn("SUBSCRIBE rejected — authenticated group does not match destination {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription: wrong group");
            }

            if (groupMatcher.group(2) != null && !identity.get().isHeadman()) {
                log.warn("SUBSCRIBE rejected — non-headman subscribing to headman topic {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription: headman only");
            }
        }

        if (registry.admit(accessor.getSessionId()).isEmpty()) {
            log.warn("SUBSCRIBE rejected — live session authority is unavailable or revoked");
            throw new IllegalArgumentException("Unauthorized subscription");
        }
        return message;
    }

    private static Long parsePositiveId(String rawValue) {
        try {
            long value = Long.parseLong(rawValue);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
