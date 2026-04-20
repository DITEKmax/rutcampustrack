package ru.rutcampustrack.attendance.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

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
     */
    public void publishMarked(AttendanceDocument doc) {
        Map<String, Object> payload = Map.of(
                "lesson_id", doc.getLessonId(),
                "user_id", doc.getUserId(),
                "group_id", doc.getGroupId(),
                "status", doc.getStatus().name().toLowerCase(),
                "marked_by", doc.getSource().name().toLowerCase()
        );

        Map<String, Object> envelope = EventEnvelope.build(EVENT_TYPE, payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize attendance.marked", e);
        }
        outboxStorage.save(EVENT_TYPE, json);
    }
}
