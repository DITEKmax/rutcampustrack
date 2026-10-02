package ru.rutcampustrack.notification.preferences;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

/** Create namespaces outside first multi-document transaction; prefs and receipts have no TTL. */
@Configuration
public class NotificationPreferencesMongoConfig {
    private final MongoTemplate mongo;
    public NotificationPreferencesMongoConfig(MongoTemplate mongo) { this.mongo = mongo; }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        for (String collection : List.of(NotificationPreferencesStore.USERS, NotificationPreferencesStore.TELEGRAM,
                NotificationPreferencesStore.LEGACY_OWNERS, NotificationPreferencesStore.RECEIPTS)) {
            if (!mongo.collectionExists(collection)) mongo.createCollection(collection);
            if (mongo.getCollection(collection).listIndexes().into(new java.util.ArrayList<>()).stream()
                    .noneMatch(index -> "_id_".equals(index.getString("name")))) {
                throw new IllegalStateException("Notification preferences identity index is missing");
            }
        }
    }
}
