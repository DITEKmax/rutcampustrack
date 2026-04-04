package ru.rutcampustrack.attendance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;

import java.util.Map;

/**
 * Generic RabbitMQ event consumer for the Attendance Service.
 * Reads event_type from the envelope and routes to domain-specific handlers.
 * Delegates lesson lifecycle logic to LessonEventService.
 * Semester cache refresh on semester.archived handled directly via SemesterCacheService.
 * <p>
 * CRITICAL: ((Number) value).longValue() is used for all numeric ID extractions.
 * Jackson deserializes JSON integers as Integer when target is Object — direct Long cast would throw ClassCastException.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventConsumer {

    private final LessonEventService lessonEventService;
    private final SemesterCacheService semesterCacheService;

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
        Map<String, Object> payload = extractPayload(envelope);
        Long lessonId = extractLong(payload, "lesson_id");
        log.debug("lesson.started: no-op (lesson_id={})", lessonId);
    }

    private void handleLessonClosed(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        Long lessonId = extractLong(payload, "lesson_id");
        Long groupId = extractLong(payload, "group_id");
        lessonEventService.processLessonClosed(lessonId, groupId);
    }

    private void handleLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        Long lessonId = extractLong(payload, "lesson_id");
        lessonEventService.processLessonCancelled(lessonId);
    }

    private void handleSemesterArchived(Map<String, Object> envelope) {
        semesterCacheService.refresh();
        log.info("semester.archived: refreshed semester cache");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("payload");
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return ((Number) value).longValue();
    }
}
