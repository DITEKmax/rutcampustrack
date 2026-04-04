package ru.rutcampustrack.attendance.event;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes attendance events to RabbitMQ fanout exchange.
 * Per D-20, D-21, D-22: constructs attendance.marked envelope per event-schemas/attendance.marked.json.
 *
 * CRITICAL: routing key is "" (empty string) — fanout exchange ignores routing key,
 *           but RabbitTemplate.convertAndSend requires the parameter.
 * CRITICAL: uses existing rabbitTemplate bean from RabbitConfig (already has Jackson2JsonMessageConverter).
 *           Do NOT declare a new RabbitTemplate bean.
 */
@Service
public class AttendanceEventPublisher {

    private static final String EXCHANGE = "rut-uit.events";

    private final RabbitTemplate rabbitTemplate;

    public AttendanceEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publishes an attendance.marked event for the given attendance document.
     * Envelope structure follows event-schemas/attendance.marked.json.
     *
     * @param doc the attendance document that was created or updated
     */
    public void publishMarked(AttendanceDocument doc) {
        Map<String, Object> payload = Map.of(
                "lesson_id", doc.getLessonId(),
                "user_id", doc.getUserId(),
                "group_id", doc.getGroupId(),
                "status", doc.getStatus().name().toLowerCase(),
                "marked_by", doc.getSource().name().toLowerCase()
        );

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", "attendance.marked");
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", payload);

        rabbitTemplate.convertAndSend(EXCHANGE, "", envelope);
    }
}
