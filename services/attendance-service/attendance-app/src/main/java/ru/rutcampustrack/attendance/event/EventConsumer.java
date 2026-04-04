package ru.rutcampustrack.attendance.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Generic RabbitMQ event consumer for the Attendance Service.
 * Reads event_type from the envelope and routes to domain-specific handlers.
 * Stub implementations will be filled in Phase 16 (business logic).
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    @RabbitListener(queues = "attendance-service.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        log.debug("Received event: {}", eventType);
        switch (eventType) {
            case "lesson.started"    -> handleLessonStarted(envelope);
            case "lesson.closed"     -> handleLessonClosed(envelope);
            case "lesson.cancelled"  -> handleLessonCancelled(envelope);
            case "semester.archived" -> handleSemesterArchived(envelope);
            default -> log.debug("Ignoring unknown event type: {}", eventType);
        }
    }

    private void handleLessonStarted(Map<String, Object> envelope) {
        // Phase 16 implements this
        log.debug("lesson.started stub -- no-op in Phase 15");
    }

    private void handleLessonClosed(Map<String, Object> envelope) {
        // Phase 16 implements auto-absent logic
        log.debug("lesson.closed stub -- no-op in Phase 15");
    }

    private void handleLessonCancelled(Map<String, Object> envelope) {
        // Phase 16 implements cancellation propagation
        log.debug("lesson.cancelled stub -- no-op in Phase 15");
    }

    private void handleSemesterArchived(Map<String, Object> envelope) {
        // Phase 16 wires SemesterCacheService.refresh() here
        log.debug("semester.archived stub -- no-op in Phase 15");
    }
}
