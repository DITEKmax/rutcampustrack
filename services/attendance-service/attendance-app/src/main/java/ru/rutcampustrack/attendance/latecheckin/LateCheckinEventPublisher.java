package ru.rutcampustrack.attendance.latecheckin;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    private static final String EXCHANGE = "rut-uit.events";
    private static final String EVENT_REQUESTED = "late_checkin.requested";
    private static final String EVENT_DECIDED = "late_checkin.decided";

    private final RabbitTemplate rabbitTemplate;

    public LateCheckinEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
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

        rabbitTemplate.convertAndSend(EXCHANGE, "", buildEnvelope(EVENT_REQUESTED, payload));
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

        rabbitTemplate.convertAndSend(EXCHANGE, "", buildEnvelope(EVENT_DECIDED, payload));
    }

    private Map<String, Object> buildEnvelope(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", eventType);
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", payload);
        return envelope;
    }
}
