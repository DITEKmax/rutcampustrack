package ru.rutcampustrack.notification.history;

import java.time.Instant;
import java.util.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.notification.contract.enums.NotificationType;

/**
 * Keeps unique(event,user), unioning missing role projections without changing any existing read
 * state or TTL.
 */
@Component
public class NotificationRoleHistoryWriter {
    private final MongoTemplate mongo;

    public NotificationRoleHistoryWriter(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public void persist(
            String eventId,
            long userId,
            NotificationType type,
            Map<String, Object> payload,
            String traceId,
            Set<String> roles,
            Instant sentAt) {
        if (userId <= 0
                || roles.isEmpty()
                || !NotificationCatalog.meaning(type).roles().containsAll(roles))
            throw new IllegalArgumentException("History recipient is required");
        Map<String, NotificationHistoryDocument.RoleView> views = new LinkedHashMap<>();
        roles.forEach(role -> views.put(role, view(sentAt)));
        Query identity =
                Query.query(Criteria.where("event_id").is(eventId).and("user_id").is(userId));
        mongo.upsert(
                identity,
                new Update()
                        .setOnInsert("eventId", eventId)
                        .setOnInsert("userId", userId)
                        .setOnInsert("type", type)
                        .setOnInsert("payload", payload)
                        .setOnInsert("sentAt", sentAt)
                        .setOnInsert("traceId", traceId)
                        .setOnInsert("roleViews", views),
                NotificationHistoryDocument.class);
        NotificationHistoryDocument stored =
                mongo.findOne(identity, NotificationHistoryDocument.class);
        if (stored == null || stored.getType() != type || stored.getSentAt() == null)
            throw new IllegalStateException("History event identity is invalid");
        for (String role : roles) {
            Query missing =
                    Query.query(
                            Criteria.where("event_id")
                                    .is(eventId)
                                    .and("user_id")
                                    .is(userId)
                                    .and("role_views." + role)
                                    .exists(false));
            mongo.updateFirst(
                    missing,
                    new Update().set("role_views." + role, view(stored.getSentAt())),
                    NotificationHistoryDocument.class);
        }
    }

    private static NotificationHistoryDocument.RoleView view(Instant sentAt) {
        return NotificationHistoryDocument.RoleView.builder().orderAt(sentAt).revision(0).build();
    }
}
