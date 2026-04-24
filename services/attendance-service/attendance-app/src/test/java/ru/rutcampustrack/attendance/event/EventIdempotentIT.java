package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.attendance.integration.AbstractAttendanceIntegrationTest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * M13 G8 — IT для consumer-side dedup в attendance-app.
 *
 * <p>Публикует один и тот же event два раза (с одним {@code event_id}) и
 * проверяет что collection {@code event_consumer_processed} содержит
 * ровно одну запись для consumer'а {@code "attendance"}.
 *
 * <p>Используется {@code semester.archived} — handler не имеет
 * persistent-эффектов (только cache refresh), поэтому IT надёжно
 * проверяет именно guard, а не побочные delete'ы.
 */
class EventIdempotentIT extends AbstractAttendanceIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final String COLLECTION = "event_consumer_processed";
    private static final String CONSUMER_ID = EventConsumer.CONSUMER_ID;

    @BeforeEach
    void cleanCollection() {
        mongoTemplate.remove(new Query(Criteria.where("consumer_id").is(CONSUMER_ID)), COLLECTION);
    }

    private Map<String, Object> envelope(UUID eventId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("event_type", "semester.archived");
        envelope.put("event_id", eventId.toString());
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("payload", Map.of("semester_id", 1L));
        return envelope;
    }

    private void publish(Map<String, Object> env) {
        rabbitTemplate.convertAndSend("rut-uit.events", "", env);
    }

    private long claimsForEvent(UUID eventId) {
        return mongoTemplate.count(
                new Query(Criteria.where("consumer_id").is(CONSUMER_ID)
                        .and("event_id").is(eventId.toString())),
                COLLECTION);
    }

    @Test
    void duplicateDelivery_isDedupped() {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = envelope(eventId);

        publish(envelope);
        publish(envelope);

        // claim запись появляется один раз — даже если оба сообщения дошли,
        // второй tryClaim ловит DuplicateKey и skip'ает.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(claimsForEvent(eventId)).isEqualTo(1L);
        });

        // Sanity: повторная публикация ещё раз — count остаётся 1.
        publish(envelope);
        await().pollDelay(2, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(claimsForEvent(eventId)).isEqualTo(1L);
        });
    }

    @Test
    void differentEventIds_areIndependent() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        publish(envelope(id1));
        publish(envelope(id2));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(claimsForEvent(id1)).isEqualTo(1L);
            assertThat(claimsForEvent(id2)).isEqualTo(1L);
        });
    }
}
