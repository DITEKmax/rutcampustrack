package ru.rutcampustrack.notification.history;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import ru.rutcampustrack.notification.push.NotificationPushIntentStore;

/**
 * Own new collections exist before transactional writes; retention matches the canonical history.
 */
@Configuration
public class NotificationDeliveryMongoConfig {
    private final MongoTemplate mongo;
    private final int ttlDays;

    public NotificationDeliveryMongoConfig(
            MongoTemplate mongo, @Value("${notification.history.ttl-days:30}") int ttlDays) {
        this.mongo = mongo;
        this.ttlDays = ttlDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void indexes() {
        for (String collection :
                java.util.List.of(
                        NotificationAudienceService.COLLECTION,
                        NotificationPushIntentStore.COLLECTION)) {
            if (!mongo.collectionExists(collection)) mongo.createCollection(collection);
            mongo.indexOps(collection)
                    .ensureIndex(
                            new Index()
                                    .on("created_at", Sort.Direction.ASC)
                                    .named("ttl_created_at")
                                    .expire(Duration.ofDays(ttlDays)));
        }
        mongo.indexOps(NotificationPushIntentStore.COLLECTION)
                .ensureIndex(
                        new Index()
                                .on("state", Sort.Direction.ASC)
                                .on("retry_at", Sort.Direction.ASC)
                                .named("state_retry_at"));
    }
}
