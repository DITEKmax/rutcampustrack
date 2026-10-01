package ru.rutcampustrack.notification.event;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;

import java.util.Map;
import java.util.UUID;

/** Rabbit boundary keeps committed replay reads outside the delivery transaction. */
@Component
public class NotificationEventConsumer {

    private final MongoTemplate mongoTemplate;
    private final EventConsumer processor;

    public NotificationEventConsumer(MongoTemplate mongoTemplate, EventConsumer processor) {
        this.mongoTemplate = mongoTemplate;
        this.processor = processor;
    }

    @RabbitListener(queues = "notification-web.events",
            containerFactory = "notificationHistoryRabbitListenerContainerFactory")
    public void onEvent(Map<String, Object> envelope) {
        String eventId = validEventId(envelope);
        if (eventId != null && mongoTemplate.exists(Query.query(
                Criteria.where("consumer_id").is(EventConsumer.CONSUMER_ID)
                        .and("event_id").is(eventId)), MongoIdempotencyStore.DEFAULT_COLLECTION)) {
            return;
        }
        // A concurrent, still-uncommitted event is invisible here. The processor's
        // transactional claim arbitrates it; rollback/retry returns to this boundary.
        processor.onEvent(envelope);
    }

    private static String validEventId(Map<String, Object> envelope) {
        if (envelope == null || envelope.get("event_id") == null) {
            return null;
        }
        try {
            return UUID.fromString(envelope.get("event_id").toString()).toString();
        } catch (IllegalArgumentException ignored) {
            // Preserve the processor/IdempotencyGuard's malformed-event rejection.
            return null;
        }
    }
}
