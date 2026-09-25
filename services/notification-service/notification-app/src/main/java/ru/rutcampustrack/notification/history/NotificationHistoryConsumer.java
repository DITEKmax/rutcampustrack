package ru.rutcampustrack.notification.history;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.outbox.mongo.MongoIdempotencyStore;

import java.util.Map;
import java.util.UUID;

/** Rabbit boundary for durable personal notification history. */
@Component
public class NotificationHistoryConsumer {

    public static final String CONSUMER_ID = "notification-history";

    private final MongoTemplate mongoTemplate;
    private final NotificationHistoryEventProcessor processor;

    public NotificationHistoryConsumer(MongoTemplate mongoTemplate,
                                       NotificationHistoryEventProcessor processor) {
        this.mongoTemplate = mongoTemplate;
        this.processor = processor;
    }

    @RabbitListener(
            queues = "notification-web.history",
            containerFactory = "notificationHistoryRabbitListenerContainerFactory")
    @EventIdempotent(consumer = CONSUMER_ID)
    public void onEvent(Map<String, Object> envelope) {
        String eventId = validEventId(envelope);
        if (eventId != null && isAlreadyProcessed(eventId)) {
            return;
        }
        processor.persist(envelope);
    }

    /**
     * Avoids opening a transaction for the common, already-committed replay
     * path. A duplicate-key write against an active Mongo transaction can mark
     * that transaction aborted even when the store converts it to `false`.
     */
    private boolean isAlreadyProcessed(String eventId) {
        Query query = Query.query(Criteria.where("consumer_id").is(CONSUMER_ID)
                .and("event_id").is(eventId));
        return mongoTemplate.exists(query, MongoIdempotencyStore.DEFAULT_COLLECTION);
    }

    private static String validEventId(Map<String, Object> envelope) {
        if (envelope == null) {
            return null;
        }
        Object raw = envelope.get("event_id");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString()).toString();
        } catch (IllegalArgumentException ignored) {
            // The transactional processor/IdempotencyGuard will reject it.
            return null;
        }
    }
}
