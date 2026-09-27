package ru.rutcampustrack.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionAuthInterceptorTest {

    private final SubscriptionAuthInterceptor interceptor = new SubscriptionAuthInterceptor();

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
    }

    private void send(StompCommand command, String destination, Map<String, Object> attributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setSessionAttributes(attributes);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);
    }

    private static Map<String, Object> identity(long userId, long groupId, boolean headman) {
        return Map.of("user_id", userId, "group_id", groupId, "is_headman", headman);
    }
}
