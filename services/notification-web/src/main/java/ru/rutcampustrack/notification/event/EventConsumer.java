package ru.rutcampustrack.notification.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class EventConsumer {

    @RabbitListener(queues = "notification-web.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        log.info("[notification-web] Received event: {}", eventType);
        // Phase 21 will add actual WebSocket routing
    }
}
