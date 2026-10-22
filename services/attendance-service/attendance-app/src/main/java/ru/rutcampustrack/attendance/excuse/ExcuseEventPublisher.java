package ru.rutcampustrack.attendance.excuse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.event.EventEnvelope;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.studentrequest.entity.RequestAttachmentDescriptorDocument;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * Publishes excuse lifecycle events to the RabbitMQ fanout exchange.
 *
 * <p>Implements decisions:
 *
 * <ul>
 *   <li>D-19: {@code excuse.requested} after {@link ExcuseService#createExcuse}
 *   <li>D-20: {@code excuse.decided} after {@link ExcuseService#updateStatus}
 *   <li>D-27: payload shape is consumed by notification-bot
 *       (bot/notifications/headman_alerts.py:29-55) — it reads {@code payload.user_id}, {@code
 *       payload.group_id}, {@code payload.student_name}, {@code payload.excuse_type}.
 * </ul>
 *
 * <p>The envelope matches the project-wide convention (see {@code
 * event-schemas/excuse.requested.json} and the existing {@link
 * ru.rutcampustrack.attendance.event.AttendanceEventPublisher}):
 *
 * <pre>
 * { event_type, event_id, occurred_at, payload }
 * </pre>
 *
 * <p><b>D-19 lowercase enum handling.</b> Per the JSON schema, {@code excuse_type} must be
 * lowercase (e.g. {@code "illness"}, {@code "free_attendance"}). We emit the lowercase form
 * explicitly via {@code name().toLowerCase()} at publish time — this is the least-invasive option:
 * it keeps the {@link ru.rutcampustrack.attendance.contract.enums.ExcuseType} enum untouched (no
 * {@code @JsonValue}, no custom serializer registered globally), avoiding any risk of breaking
 * REST/DTO serialization that callers already depend on.
 *
 * <p>CRITICAL: routing key is {@code ""} (fanout ignores it). Uses the existing {@code
 * rabbitTemplate} bean from {@link ru.rutcampustrack.attendance.config.RabbitConfig} — do NOT
 * declare a new one.
 */
@Component
public class ExcuseEventPublisher {
    private ru.rutcampustrack.attendance.audit.RequestActionRecorder requestActions;

    @org.springframework.beans.factory.annotation.Autowired
    public void setRequestActions(
            ru.rutcampustrack.attendance.audit.RequestActionRecorder actions) {
        this.requestActions = actions;
    }

    private static final String EVENT_REQUESTED = "excuse.requested";
    private static final String EVENT_DECIDED = "excuse.decided";

    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public ExcuseEventPublisher(OutboxStorage outboxStorage, ObjectMapper objectMapper) {
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes an {@code excuse.requested} event for a freshly created ticket. Invoked from {@link
     * ExcuseService#createExcuse} after the repository save.
     *
     * <p>{@code lessonDetails} — обогащение, которое staros/a должен видеть в Telegram и в
     * веб-уведомлении (номер пары, дата, предмет). Если null/пусто, payload по-прежнему содержит
     * только {@code lesson_ids}, без поломки существующих потребителей.
     */
    public void publishRequested(ExcuseTicket ticket, List<Map<String, Object>> lessonDetails) {
        saveToOutbox(EVENT_REQUESTED, buildRequestedPayload(ticket, lessonDetails));
        if (requestActions != null)
            requestActions.excuse(
                    ticket, null, ticket.getStudentId(), "STUDENT", ticket.getCreatedAt());
    }

    /**
     * Publishes the student-request representation with attachment metadata. Attachment bytes are
     * retained in {@code request_attachments}; the outbox carries descriptors only so a retry
     * cannot duplicate or expose binary data.
     */
    public void publishRequested(
            ExcuseTicket ticket,
            List<Map<String, Object>> lessonDetails,
            List<RequestAttachmentDescriptorDocument> descriptors) {
        Map<String, Object> payload = buildRequestedPayload(ticket, lessonDetails);
        List<Map<String, Object>> attachments = new ArrayList<>();
        if (descriptors != null) {
            for (RequestAttachmentDescriptorDocument descriptor : descriptors) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", descriptor.getId());
                item.put("name", descriptor.getName());
                item.put("content_type", descriptor.getContentType());
                item.put("size", descriptor.getSize());
                item.put("sha256", descriptor.getSha256());
                item.put(
                        "state",
                        descriptor.getState() == null
                                ? null
                                : descriptor.getState().name().toLowerCase());
                item.put(
                        "uploaded_at",
                        descriptor.getUploadedAt() == null
                                ? null
                                : descriptor.getUploadedAt().toString());
                item.put(
                        "expires_at",
                        descriptor.getExpiresAt() == null
                                ? null
                                : descriptor.getExpiresAt().toString());
                attachments.add(item);
            }
        }
        payload.put("attachments", attachments);
        payload.put("has_attachments", !attachments.isEmpty());
        saveToOutbox(EVENT_REQUESTED, payload);
        if (requestActions != null)
            requestActions.excuse(
                    ticket, null, ticket.getStudentId(), "STUDENT", ticket.getCreatedAt());
    }

    private Map<String, Object> buildRequestedPayload(
            ExcuseTicket ticket, List<Map<String, Object>> lessonDetails) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", ticket.getId());
        payload.put("student_id", ticket.getStudentId());
        // D-27: bot reads payload.user_id — duplicate student_id under the key the bot expects.
        payload.put("user_id", ticket.getStudentId());
        payload.put("student_name", ticket.getStudentName());
        payload.put("group_id", ticket.getGroupId());
        payload.put("lesson_ids", ticket.getLessonIds());
        payload.put("lessons", lessonDetails != null ? lessonDetails : new ArrayList<>());
        // D-19: lowercase enum value (schema:
        // illness|summons|university_order|exemption|free_attendance|other).
        payload.put(
                "excuse_type",
                ticket.getExcuseType() != null
                        ? ticket.getExcuseType().name().toLowerCase()
                        : null);
        payload.put("comment", ticket.getComment());
        payload.put(
                "created_at",
                ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : null);
        return payload;
    }

    /**
     * Publishes an {@code excuse.decided} event for a ticket that has just transitioned to {@code
     * APPROVED} or {@code REJECTED}. Invoked from {@link ExcuseService#updateStatus} AFTER the
     * attendance cascade and BEFORE returning — journal consistency depends on the cascade landing
     * before any downstream consumer observes the decision.
     */
    public void publishDecided(ExcuseTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", ticket.getId());
        payload.put("student_id", ticket.getStudentId());
        payload.put("user_id", ticket.getStudentId());
        payload.put("group_id", ticket.getGroupId());
        payload.put("decision_by", ticket.getDecisionBy());
        payload.put(
                "status",
                ticket.getStatus() != null ? ticket.getStatus().name().toLowerCase() : null);
        payload.put("decision_comment", ticket.getDecisionComment());
        payload.put(
                "decided_at",
                ticket.getDecisionAt() != null ? ticket.getDecisionAt().toString() : null);
        if (ticket.getDecisionSelection() != null) {
            var selection = ticket.getDecisionSelection();
            payload.put("request_key", selection.getRequestKey());
            payload.put("expected_revision", selection.getExpectedRevision());
            payload.put(
                    "included_lesson_ids",
                    selection.getIncludedLessons().stream()
                            .map(
                                    ru.rutcampustrack.attendance.studentrequest.entity
                                                    .StudentLessonSnapshotDocument
                                            ::getLessonId)
                            .toList());
            payload.put(
                    "excluded_lesson_ids",
                    selection.getExcludedLessons().stream()
                            .map(
                                    ru.rutcampustrack.attendance.studentrequest.entity
                                                    .StudentLessonSnapshotDocument
                                            ::getLessonId)
                            .toList());
            payload.put(
                    "included_occurrence_ids",
                    selection.getIncludedLessons().stream()
                            .map(
                                    ru.rutcampustrack.attendance.studentrequest.entity
                                                    .StudentLessonSnapshotDocument
                                            ::getOccurrenceId)
                            .toList());
            payload.put(
                    "excluded_occurrence_ids",
                    selection.getExcludedLessons().stream()
                            .map(
                                    ru.rutcampustrack.attendance.studentrequest.entity
                                                    .StudentLessonSnapshotDocument
                                            ::getOccurrenceId)
                            .toList());
            payload.put(
                    "excluded_lessons",
                    selection.getExcludedLessons().stream()
                            .map(ExcuseEventPublisher::excludedLessonPayload)
                            .toList());
        }

        saveToOutbox(EVENT_DECIDED, payload);
    }

    /** Display metadata comes only from the immutable decision receipt, never current Schedule. */
    private static Map<String, Object> excludedLessonPayload(StudentLessonSnapshotDocument lesson) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lesson_id", lesson.getLessonId());
        payload.put("occurrence_id", lesson.getOccurrenceId());
        payload.put("date", lesson.getDate() == null ? null : lesson.getDate().toString());
        payload.put("lesson_number", lesson.getLessonNumber());
        payload.put("subject_name", lesson.getSubjectName());
        payload.put("lesson_type", lesson.getSubjectType());
        return payload;
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
        if (EVENT_REQUESTED.equals(eventType)) {
            try {
                outboxStorage.save(
                        "dashboard.invalidated",
                        objectMapper.writeValueAsString(
                                EventEnvelope.build("dashboard.invalidated", Map.of())));
            } catch (JsonProcessingException failure) {
                throw new IllegalStateException(
                        "Failed to serialize dashboard invalidation", failure);
            }
        }
    }
}
