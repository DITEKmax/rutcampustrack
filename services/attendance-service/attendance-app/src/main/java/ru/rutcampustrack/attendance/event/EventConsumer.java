package ru.rutcampustrack.attendance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;

import java.time.LocalDate;
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
            case "lesson.started"          -> handleLessonStarted(envelope);
            case "lesson.closed"           -> handleLessonClosed(envelope);
            case "lesson.cancelled"        -> handleLessonCancelled(envelope);
            case "lesson.one_off.cancelled" -> handleOneOffLessonCancelled(envelope);
            case "semester.archived"       -> handleSemesterArchived(envelope);
            default -> log.debug("Ignoring unknown event type: {}", eventType);
        }
    }

    private void handleLessonStarted(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Long lessonId = extractLong(payload, "lesson_id");
        log.debug("lesson.started: no-op (lesson_id={})", lessonId);
    }

    private void handleLessonClosed(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Long lessonId = extractLong(payload, "lesson_id");
        Long groupId = extractLong(payload, "group_id");
        lessonEventService.processLessonClosed(lessonId, groupId);
    }

    private void handleLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Long lessonId = extractLong(payload, "lesson_id");
        lessonEventService.processLessonCancelled(lessonId);
    }

    /**
     * D-22 / AC-08: cascade-delete attendance docs for a cancelled one-off lesson.
     * Natural key: {@code (group_id, lesson_date, lesson_number)}.
     * Idempotent — repeated delivery yields 0 deletes without exception.
     */
    private void handleOneOffLessonCancelled(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Long groupId = extractLong(payload, "group_id");
        String dateStr = (String) payload.get("date");
        Object lessonNumberRaw = payload.get("lesson_number");
        if (groupId == null || dateStr == null || lessonNumberRaw == null) {
            log.warn("lesson.one_off.cancelled: missing required fields, ignoring: {}", payload);
            return;
        }
        LocalDate date = LocalDate.parse(dateStr);
        Integer lessonNumber = ((Number) lessonNumberRaw).intValue();
        lessonEventService.processOneOffLessonCancelled(groupId, date, lessonNumber);
    }

    private void handleSemesterArchived(Map<String, Object> envelope) {
        semesterCacheService.refresh();
        log.info("semester.archived: refreshed semester cache");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayload(Map<String, Object> envelope) {
        Object raw = envelope.get("payload");
        if (raw == null) {
            log.warn("Event envelope has no 'payload' field: {}", envelope);
            return null;
        }
        return (Map<String, Object>) raw;
    }

    private Long extractLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        return ((Number) value).longValue();
    }
}
