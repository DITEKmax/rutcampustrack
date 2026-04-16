package ru.rutcampustrack.attendance.excuse;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;

import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes excuse lifecycle events to the RabbitMQ fanout exchange.
 *
 * <p>Implements decisions:
 * <ul>
 *   <li>D-19: {@code excuse.requested} after {@link ExcuseService#createExcuse}</li>
 *   <li>D-20: {@code excuse.decided}   after {@link ExcuseService#updateStatus}</li>
 *   <li>D-27: payload shape is consumed by notification-bot
 *       (bot/notifications/headman_alerts.py:29-55) — it reads
 *       {@code payload.user_id}, {@code payload.group_id},
 *       {@code payload.student_name}, {@code payload.excuse_type}.</li>
 * </ul>
 *
 * <p>The envelope matches the project-wide convention
 * (see {@code event-schemas/excuse.requested.json} and the existing
 * {@link ru.rutcampustrack.attendance.event.AttendanceEventPublisher}):
 * <pre>
 * { event_type, event_id, occurred_at, payload }
 * </pre>
 *
 * <p><b>D-19 lowercase enum handling.</b> Per the JSON schema, {@code excuse_type}
 * must be lowercase (e.g. {@code "illness"}, {@code "free_attendance"}). We emit
 * the lowercase form explicitly via {@code name().toLowerCase()} at publish time —
 * this is the least-invasive option: it keeps the {@link ru.rutcampustrack.attendance.contract.enums.ExcuseType}
 * enum untouched (no {@code @JsonValue}, no custom serializer registered globally),
 * avoiding any risk of breaking REST/DTO serialization that callers already depend on.
 *
 * <p>CRITICAL: routing key is {@code ""} (fanout ignores it). Uses the existing
 * {@code rabbitTemplate} bean from {@link ru.rutcampustrack.attendance.config.RabbitConfig}
 * — do NOT declare a new one.
 */
@Component
public class ExcuseEventPublisher {

    private static final String EXCHANGE = "rut-uit.events";
    private static final String EVENT_REQUESTED = "excuse.requested";
    private static final String EVENT_DECIDED = "excuse.decided";

    private final RabbitTemplate rabbitTemplate;

    public ExcuseEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes an {@code excuse.requested} event for a freshly created ticket.
     * Invoked from {@link ExcuseService#createExcuse} after the repository save.
     */
    public void publishRequested(ExcuseTicket ticket) {
        rabbitTemplate.convertAndSend(EXCHANGE, "",
                buildEnvelope(EVENT_REQUESTED, buildRequestedPayload(ticket)));
    }

    /**
     * Variant of {@link #publishRequested(ExcuseTicket)} that carries a
     * supporting document. The document is base64-encoded so it can travel
     * inside the JSON envelope; notification-bot decodes it and forwards to
     * Telegram via {@code send_document}. The server never persists the file —
     * once the message is acked by the bot, the bytes are gone.
     */
    public void publishRequestedWithFile(ExcuseTicket ticket,
                                         String fileName,
                                         String contentType,
                                         byte[] fileBytes) {
        Map<String, Object> payload = buildRequestedPayload(ticket);
        if (fileBytes != null && fileBytes.length > 0) {
            payload.put("file_name", fileName);
            payload.put("file_mime_type", contentType);
            payload.put("file_size", fileBytes.length);
            payload.put("file_payload_b64", Base64.getEncoder().encodeToString(fileBytes));
        }
        rabbitTemplate.convertAndSend(EXCHANGE, "", buildEnvelope(EVENT_REQUESTED, payload));
    }

    private Map<String, Object> buildRequestedPayload(ExcuseTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", ticket.getId());
        payload.put("student_id", ticket.getStudentId());
        // D-27: bot reads payload.user_id — duplicate student_id under the key the bot expects.
        payload.put("user_id", ticket.getStudentId());
        payload.put("student_name", ticket.getStudentName());
        payload.put("group_id", ticket.getGroupId());
        payload.put("lesson_ids", ticket.getLessonIds());
        // D-19: lowercase enum value (schema: illness|summons|university_order|exemption|free_attendance|other).
        payload.put("excuse_type",
                ticket.getExcuseType() != null ? ticket.getExcuseType().name().toLowerCase() : null);
        payload.put("comment", ticket.getComment());
        payload.put("created_at", ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null);
        return payload;
    }

    /**
     * Publishes an {@code excuse.decided} event for a ticket that has just
     * transitioned to {@code APPROVED} or {@code REJECTED}.
     * Invoked from {@link ExcuseService#updateStatus} AFTER the attendance cascade
     * and BEFORE returning — journal consistency depends on the cascade landing
     * before any downstream consumer observes the decision.
     */
    public void publishDecided(ExcuseTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", ticket.getId());
        payload.put("student_id", ticket.getStudentId());
        payload.put("user_id", ticket.getStudentId());
        payload.put("decision_by", ticket.getDecisionBy());
        payload.put("status",
                ticket.getStatus() != null ? ticket.getStatus().name().toLowerCase() : null);
        payload.put("decision_comment", ticket.getDecisionComment());
        payload.put("decided_at",
                ticket.getDecisionAt() != null ? ticket.getDecisionAt().toString() : null);

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
