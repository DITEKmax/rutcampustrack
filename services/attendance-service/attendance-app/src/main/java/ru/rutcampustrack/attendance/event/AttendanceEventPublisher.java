package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Публикует attendance-события в outbox (M02 Группа 5).
 *
 * <p>Раньше шёл прямой {@code rabbitTemplate.convertAndSend(...)}. Теперь
 * envelope сериализуется в JSON и записывается в {@code attendance_outbox}
 * через {@link OutboxStorage}. Публикацию в Rabbit выполняет
 * {@code OutboxPublisherJob} асинхронно (shared-outbox).
 *
 * <p>Caller должен быть {@code @Transactional} (Mongo replica set) — тогда
 * write в outbox атомарен с доменной операцией.
 *
 * <p>Per D-20, D-21, D-22: envelope структура следует
 * {@code event-schemas/attendance.marked.json}.
 */
@Service
public class AttendanceEventPublisher {

    private static final String EVENT_TYPE = "attendance.marked";

    private final OutboxStorage outboxStorage;
    private final ObjectMapper objectMapper;

    public AttendanceEventPublisher(OutboxStorage outboxStorage, ObjectMapper objectMapper) {
        this.outboxStorage = outboxStorage;
        this.objectMapper = objectMapper;
    }

    /**
     * Публикует attendance.marked event для документа.
     *
     * <p>{@code subjectName} — опциональное обогащение для consumer'ов
     * (NOTIF unification): notification-bot и frontends показывают студенту
     * "Староста поставил «Х» на паре №N {Предмет}" при manual mark
     * ({@code marked_by="headman"}). Если null/пусто — поле просто не
     * появляется в payload, существующие consumer'ы не падают.
     */
    public void publishMarked(AttendanceDocument doc, String subjectName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lesson_id", doc.getLessonId());
        payload.put("user_id", doc.getUserId());
        payload.put("group_id", doc.getGroupId());
        payload.put("status", doc.getStatus().name().toLowerCase());
        payload.put("marked_by", doc.getSource().name().toLowerCase());
        if (doc.getLessonNumber() != null) {
            payload.put("lesson_number", doc.getLessonNumber());
        }
        if (doc.getLessonDate() != null) {
            payload.put("lesson_date", doc.getLessonDate().toString());
        }
        if (doc.getSubjectId() != null) {
            payload.put("subject_id", doc.getSubjectId());
        }
        if (subjectName != null && !subjectName.isBlank()) {
            payload.put("subject_name", subjectName);
        }

        Map<String, Object> envelope = EventEnvelope.build(EVENT_TYPE, payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize attendance.marked", e);
        }
        outboxStorage.save(EVENT_TYPE, json);
    }

    /**
     * Backward-compat overload — для callers, которые ещё не передают
     * subjectName (auto-absent через LessonEventService, self check-in).
     */
    public void publishMarked(AttendanceDocument doc) {
        publishMarked(doc, null);
    }
}
