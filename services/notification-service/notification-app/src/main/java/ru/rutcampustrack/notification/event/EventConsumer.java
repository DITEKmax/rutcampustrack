package ru.rutcampustrack.notification.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class EventConsumer {

    private static final Set<String> HEADMAN_ONLY_EVENTS = Set.of(
            "excuse.requested",
            "late_checkin.requested"
    );

    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushDeliveryService webPushDeliveryService;

    public EventConsumer(SimpMessagingTemplate messagingTemplate,
                         WebPushDeliveryService webPushDeliveryService) {
        this.messagingTemplate = messagingTemplate;
        this.webPushDeliveryService = webPushDeliveryService;
    }

    @RabbitListener(queues = "notification-web.events")
    @SuppressWarnings("unchecked")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }

        Map<String, Object> payload = (Map<String, Object>) envelope.get("payload");
        if (payload == null) {
            log.warn("Event {} has no payload, ignoring", eventType);
            return;
        }

        Number groupIdNum = (Number) payload.get("group_id");
        if (groupIdNum == null) {
            log.debug("Event {} has no group_id in payload, skipping WebSocket routing", eventType);
            return;
        }
        long groupId = groupIdNum.longValue();

        // D-05, Pitfall 2: Headman-only events go to separate sub-topic
        // so non-headman subscribers on /topic/group/{groupId} never see them
        String destination = HEADMAN_ONLY_EVENTS.contains(eventType)
                ? "/topic/group/" + groupId + "/headman"
                : "/topic/group/" + groupId;

        // D-06: Wrap in {type, payload} envelope — no enrichment
        Map<String, Object> wsMessage = Map.of("type", eventType, "payload", payload);
        messagingTemplate.convertAndSend(destination, wsMessage);
        log.debug("Routed {} to {}", eventType, destination);

        // D-07, D-08: After STOMP delivery — trigger async Web Push for push-eligible events.
        // sendToGroup is @Async so it returns immediately and does NOT block this RabbitMQ listener thread.
        if (webPushDeliveryService.shouldPush(eventType)) {
            webPushDeliveryService.sendToGroup(groupId, eventType, payload);
            log.debug("Triggered async push for {} to group {}", eventType, groupId);
        }
    }
}
