package ru.rutcampustrack.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates client subscriptions against the identity established by the ticket handshake.
 *
 * <p>Only supported group, headman, and personal destinations are allowed. Client SEND frames
 * are rejected because this service only publishes broker notifications from server consumers.
 */
@Component
@Slf4j
public class SubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/group/([1-9][0-9]*)(/headman)?$");
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/user/([1-9][0-9]*)$");

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

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("SUBSCRIBE rejected — no session attributes for destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription");
        }

        Matcher userMatcher = USER_TOPIC.matcher(destination == null ? "" : destination);
        if (userMatcher.matches()) {
            Long requestedUserId = parsePositiveId(userMatcher.group(1));
            Long sessionUserId = positiveIntegralId(sessionAttributes.get("user_id"));
            if (requestedUserId == null || sessionUserId == null || !sessionUserId.equals(requestedUserId)) {
                log.warn("SUBSCRIBE rejected — user identity does not match destination {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription");
            }
            return message;
        }

        Matcher groupMatcher = GROUP_TOPIC.matcher(destination == null ? "" : destination);
        if (!groupMatcher.matches()) {
            log.warn("SUBSCRIBE rejected — unsupported destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription");
        }

        Long requestedGroupId = parsePositiveId(groupMatcher.group(1));
        Long sessionUserId = positiveIntegralId(sessionAttributes.get("user_id"));
        Long sessionGroupId = positiveIntegralId(sessionAttributes.get("group_id"));
        if (requestedGroupId == null || sessionUserId == null || sessionGroupId == null
                || !sessionGroupId.equals(requestedGroupId)) {
            log.warn("SUBSCRIBE rejected — authenticated group does not match destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription: wrong group");
        }

        if (groupMatcher.group(2) != null) {
            boolean isHeadman = Boolean.TRUE.equals(sessionAttributes.get("is_headman"));
            if (!isHeadman) {
                log.warn("SUBSCRIBE rejected — non-headman subscribing to headman topic {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription: headman only");
            }
        }

        return message;
    }

    private static Long parsePositiveId(String rawValue) {
        try {
            return positiveIntegralId(Long.valueOf(rawValue));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long positiveIntegralId(Object rawValue) {
        try {
            long value;
            if (rawValue instanceof Byte || rawValue instanceof Short
                    || rawValue instanceof Integer || rawValue instanceof Long) {
                value = ((Number) rawValue).longValue();
            } else if (rawValue instanceof BigInteger bigInteger) {
                value = bigInteger.longValueExact();
            } else if (rawValue instanceof BigDecimal bigDecimal) {
                value = bigDecimal.longValueExact();
            } else {
                return null;
            }
            return value > 0 ? value : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }
}
