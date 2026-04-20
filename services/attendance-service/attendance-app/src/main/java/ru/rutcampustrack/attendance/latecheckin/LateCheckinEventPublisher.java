package ru.rutcampustrack.attendance.latecheckin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.event.EventEnvelope;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes late-checkin lifecycle events to the fanout exchange.
 *
 * <ul>
 *   <li>{@code late_checkin.requested} — after createRequest(), consumed by notification-bot.</li>
 *   <li>{@code late_checkin.decided}   — after applyDecision(), consumed by notification-bot
 *       (to notify the student about the outcome).</li>
 * </ul>
 *
 * Envelope matches existing publishers:
 * {@code { event_type, event_id, occurred_at, payload }}.
 */
@Component
public class LateCheckinEventPublisher {

    private static final String EVENT_REQUESTED = "late_checkin.requested";
    private static final String EVENT_DECIDED = "late_checkin.decided";

    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public LateCheckinEventPublisher(OutboxStorage outboxStorage, ObjectMapper objectMapper) {
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    public void publishRequested(
            LateCheckinRequest request,
            LocalDate lessonDate,
            Integer lessonNumber,
            Long subjectId,
            String subjectName
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getId());
        payload.put("user_id", request.getStudentId());
        payload.put("group_id", request.getGroupId());
        payload.put("lesson_id", request.getLessonId());
        payload.put("student_name", request.getStudentName());
        payload.put("lesson_date", lessonDate != null ? lessonDate.toString() : null);
        payload.put("lesson_number", lessonNumber);
        payload.put("subject_id", subjectId);
        payload.put("subject_name", subjectName);

        saveToOutbox(EVENT_REQUESTED, payload);
    }

    public void publishDecided(
            LateCheckinRequest request,
            LocalDate lessonDate,
            Integer lessonNumber,
            Long subjectId,
            String subjectName
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request_id", request.getId());
        payload.put("user_id", request.getStudentId());
        payload.put("group_id", request.getGroupId());
        payload.put("lesson_id", request.getLessonId());
        payload.put("decision_by", request.getDecisionBy());
        payload.put("status",
                request.getStatus() != null ? request.getStatus().name().toLowerCase() : null);
        payload.put("decided_at",
                request.getDecisionAt() != null ? request.getDecisionAt().toString() : null);
        payload.put("lesson_date", lessonDate != null ? lessonDate.toString() : null);
        payload.put("lesson_number", lessonNumber);
        payload.put("subject_id", subjectId);
        payload.put("subject_name", subjectName);

        saveToOutbox(EVENT_DECIDED, payload);
    }

    private void saveToOutbox(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = EventEnvelope.build(eventType, payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize " + eventType + " event", e);
        }
        outboxStorage.save(eventType, json);
    }
}
