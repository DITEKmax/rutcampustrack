package ru.rutcampustrack.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IMP-01: Validates SUBSCRIBE destinations against user's group from JWT session.
 *
 * <p>Prevents authenticated users from subscribing to topics of groups they don't belong to.
 * Headman-only topics (/topic/group/{id}/headman) additionally require is_headman=true.
 */
@Component
@Slf4j
public class SubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Pattern GROUP_TOPIC = Pattern.compile("^/topic/group/(\\d+)(/headman)?$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = GROUP_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("SUBSCRIBE rejected — no session attributes for destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription");
        }

        long requestedGroupId = Long.parseLong(matcher.group(1));
        boolean isHeadmanTopic = matcher.group(2) != null;

        Object groupIdAttr = sessionAttributes.get("group_id");
        if (groupIdAttr == null) {
            log.warn("SUBSCRIBE rejected — no group_id in session for destination {}", destination);
            throw new IllegalArgumentException("Unauthorized subscription");
        }

        long userGroupId = ((Number) groupIdAttr).longValue();
        if (userGroupId != requestedGroupId) {
            log.warn("SUBSCRIBE rejected — user group {} != requested group {} for destination {}",
                    userGroupId, requestedGroupId, destination);
            throw new IllegalArgumentException("Unauthorized subscription: wrong group");
        }

        if (isHeadmanTopic) {
            boolean isHeadman = Boolean.TRUE.equals(sessionAttributes.get("is_headman"));
            if (!isHeadman) {
                log.warn("SUBSCRIBE rejected — non-headman subscribing to headman topic {}", destination);
                throw new IllegalArgumentException("Unauthorized subscription: headman only");
            }
        }

        return message;
    }
}
