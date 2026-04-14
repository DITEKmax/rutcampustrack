package ru.rutcampustrack.notification.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 58-07 / BUG-006-6: STOMP routing + push triggering для событий
 * group.renamed / group.archived через EventConsumer.
 */
@ExtendWith(MockitoExtension.class)
class GroupEventTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private WebPushDeliveryService webPushDeliveryService;

    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventConsumer(messagingTemplate, webPushDeliveryService);
    }

    @Test
    void groupRenamed_routesToGroupTopicAndTriggersPush() {
        when(webPushDeliveryService.shouldPush("group.renamed")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 42);
        Map<String, Object> envelope = Map.of("event_type", "group.renamed", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/42"),
                eq(Map.of("type", "group.renamed", "payload", payload))
        );
        verify(webPushDeliveryService).sendToGroup(42L, "group.renamed", payload);
    }

    @Test
    void groupArchived_routesToGroupTopicAndTriggersPush() {
        when(webPushDeliveryService.shouldPush("group.archived")).thenReturn(true);
        Map<String, Object> payload = Map.of("group_id", 77);
        Map<String, Object> envelope = Map.of("event_type", "group.archived", "payload", payload);

        consumer.onEvent(envelope);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/group/77"),
                eq(Map.of("type", "group.archived", "payload", payload))
        );
        verify(webPushDeliveryService).sendToGroup(77L, "group.archived", payload);
    }
}
