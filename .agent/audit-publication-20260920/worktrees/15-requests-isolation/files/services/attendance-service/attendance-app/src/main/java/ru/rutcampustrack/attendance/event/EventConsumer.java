package ru.rutcampustrack.attendance.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.exception.ConflictException;
import ru.rutcampustrack.attendance.studentrequest.StudentRequestService;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

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
public class EventConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "attendance";

    private final LessonEventService lessonEventService;
    private final SemesterCacheService semesterCacheService;
    private final StudentRequestService studentRequestService;
    private final IdempotencyGuard idempotencyGuard;

    private static final int SUPPORTED_EVENT_VERSION = 1;
    private static final String TRUSTED_BOT_SOURCE = "notification-bot";

    @RabbitListener(queues = "attendance-service.events")
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
            return;
        }
        // M04 QA3 — extract trace_id из envelope в MDC до handler'а.
        withTraceContext(envelope, () -> {
            log.debug("Received event: {}", eventType);
            switch (eventType) {
                case "lesson.started"          -> handleLessonStarted(envelope);
                case "lesson.closed"           -> handleLessonClosed(envelope);
                case "lesson.cancelled"        -> handleLessonCancelled(envelope);
                case "lesson.deleted"          -> handleLessonDeleted(envelope);
                case "lesson.one_off.cancelled" -> handleOneOffLessonCancelled(envelope);
                case "semester.archived"       -> handleSemesterArchived(envelope);
                case "late_checkin.decision"   -> handleLateCheckinDecision(envelope);
                case "excuse.decision"         -> handleExcuseDecision(envelope);
                default -> log.debug("Ignoring unknown event type: {}", eventType);
            }
        });
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
     * Cascade-delete attendance docs when schedule-service physically removes
     * Lesson rows (e.g. ScheduleItem edit that regenerates planned lessons).
     * Drops every attendance doc whose {@code lesson_id} is in the event payload.
     */
    @SuppressWarnings("unchecked")
    private void handleLessonDeleted(Map<String, Object> envelope) {
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        Object raw = payload.get("lesson_ids");
        if (!(raw instanceof java.util.List<?> list) || list.isEmpty()) {
            log.warn("lesson.deleted: missing or empty lesson_ids, ignoring: {}", payload);
            return;
        }
        java.util.List<Long> lessonIds = new java.util.ArrayList<>(list.size());
        for (Object id : list) {
            if (id instanceof Number n) lessonIds.add(n.longValue());
        }
        if (lessonIds.isEmpty()) return;
        lessonEventService.processLessonsDeleted(lessonIds);
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

    /**
     * Consumed from notification-bot: headman pressed approve/reject on a late-checkin
     * request. Payload: {@code request_id} (string), {@code decision_by} (positive internal user_id),
     * {@code approved} (bool).
     */
    private void handleLateCheckinDecision(Map<String, Object> envelope) {
        requireTrustedBotDecisionEnvelope(envelope, "late_checkin.decision");
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        String requestId = (String) payload.get("request_id");
        Long decisionBy = extractPositiveLong(payload.get("decision_by"));
        Object approvedRaw = payload.get("approved");
        if (requestId == null || requestId.isBlank() || decisionBy == null || !(approvedRaw instanceof Boolean)) {
            throw new IllegalArgumentException("late_checkin.decision has invalid payload");
        }
        try {
            studentRequestService.decideLateCheckinFromBot(requestId, decisionBy, (Boolean) approvedRaw);
        } catch (ConflictException stale) {
            // A trusted event with a terminal request is an authenticated stale
            // delivery.  The persisted terminal detail remains authoritative;
            // acknowledge without mutating it or publishing a second event.
            log.info("Ignoring stale late_checkin.decision for request_id={}: {}", requestId,
                    stale.getMessage());
        }
    }

    /**
     * Старос/тa нажал inline-кнопку под excuse-тикетом в Telegram. Payload:
     * {@code ticket_id}, {@code decision_by} (telegram user_id), {@code approved},
     * опционально {@code decision_comment}.
     */
    private void handleExcuseDecision(Map<String, Object> envelope) {
        requireTrustedBotDecisionEnvelope(envelope, "excuse.decision");
        Map<String, Object> payload = extractPayload(envelope);
        if (payload == null) return;
        String ticketId = (String) payload.get("ticket_id");
        Long decisionBy = extractLong(payload, "decision_by");
        Object approvedRaw = payload.get("approved");
        Object commentRaw = payload.get("decision_comment");
        String comment;
        if (commentRaw == null) {
            comment = null;
        } else if (commentRaw instanceof String value) {
            comment = value;
        } else {
            throw new IllegalArgumentException("excuse.decision has invalid decision_comment");
        }
        if (ticketId == null || ticketId.isBlank() || decisionBy == null
                || decisionBy <= 0 || !(approvedRaw instanceof Boolean)) {
            throw new IllegalArgumentException("excuse.decision has invalid payload");
        }
        try {
            studentRequestService.decideExcuseFromBot(ticketId, decisionBy, (Boolean) approvedRaw, comment);
        } catch (ConflictException stale) {
            // See the late-checkin path: trusted stale commands are no-op ACKs,
            // while malformed/unauthorized failures still propagate to DLQ.
            log.info("Ignoring stale excuse.decision for ticket_id={}: {}", ticketId,
                    stale.getMessage());
        }
    }

    private void requireTrustedBotDecisionEnvelope(Map<String, Object> envelope, String expectedType) {
        Object version = envelope.get("event_version");
        Object source = envelope.get("source");
        if (!(version instanceof Number number)
                || number.intValue() != SUPPORTED_EVENT_VERSION
                || !TRUSTED_BOT_SOURCE.equals(source)
                || !expectedType.equals(envelope.get("event_type"))) {
            throw new IllegalArgumentException("Unsupported or untrusted " + expectedType + " envelope");
        }
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

    private Long extractPositiveLong(Object value) {
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            return null;
        }
        long candidate = ((Number) value).longValue();
        return candidate > 0 ? candidate : null;
    }
}
