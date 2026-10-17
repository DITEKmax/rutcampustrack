package ru.rutcampustrack.notification.push;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import ru.rutcampustrack.notification.history.NotificationAudienceClient;
import ru.rutcampustrack.notification.history.NotificationPayloadSnapshot;

/**
 * Owned durable external intent, committed in the event transaction; provider uncertainty is never
 * blindly replayed.
 */
@Repository
public class NotificationPushIntentStore {
    public static final String COLLECTION = "notification_webpush_intents";
    private static final int MAX_ATTEMPTS = 5;
    private final MongoTemplate mongo;
    private final Clock clock;

    public NotificationPushIntentStore(MongoTemplate mongo, Clock clock) {
        this.mongo = mongo;
        this.clock = clock;
    }

    public void enqueue(Map<String, Object> envelope, Map<Long, Set<String>> recipients) {
        String eventId = UUID.fromString(envelope.get("event_id").toString()).toString();
        Map<String, Object> original = NotificationAudienceClient.payload(envelope);
        String eventType = (String) envelope.get("event_type");
        long owner =
                original.get("user_id") == null
                        ? 0
                        : NotificationAudienceClient.positive(original.get("user_id"));
        Map<String, Object> payload =
                new LinkedHashMap<>(
                        NotificationPayloadSnapshot.forRecipient(eventType, original, owner));
        payload.remove("decision_comment");
        for (String id : List.of("user_id", "target_user_id", "actor_user_id")) {
            if (original.get(id) != null)
                payload.put(id, NotificationAudienceClient.positive(original.get(id)));
        }
        if ("attendance.marked".equals(eventType)) payload.put("marked_by", "headman");
        var context =
                new Document("event_id", eventId)
                        .append("event_type", eventType)
                        .append("occurred_at", envelope.get("occurred_at"))
                        .append("payload", payload);
        // This also verifies that minimal storage retained every RPC authority input.
        if (!NotificationAudienceClient.requests(envelope)
                .equals(NotificationAudienceClient.requests(context)))
            throw new IllegalArgumentException("Delivery context is incomplete");
        Document roleMap = new Document();
        recipients.forEach((id, roles) -> roleMap.put(Long.toString(id), new ArrayList<>(roles)));
        mongo.upsert(
                Query.query(Criteria.where("_id").is(eventId)),
                new Update()
                        .setOnInsert("context", context)
                        .setOnInsert("roles", roleMap)
                        .setOnInsert("state", recipients.isEmpty() ? "SKIPPED" : "PENDING")
                        .setOnInsert("attempts", 0)
                        .setOnInsert("created_at", date(now()))
                        .setOnInsert("retry_at", date(now()))
                        .setOnInsert("delivered", List.of()),
                COLLECTION);
    }

    public Document claim() {
        Instant now = now();
        // A crashed worker that might have contacted the provider is retained for manual
        // reconciliation.
        mongo.updateMulti(
                Query.query(
                        Criteria.where("state")
                                .is("RUNNING")
                                .and("lease_until")
                                .lte(date(now))
                                .and("active_key")
                                .exists(true)),
                new Update()
                        .set("state", "UNCERTAIN")
                        .set("error_kind", "WorkerLeaseExpiredDuringProviderAttempt"),
                COLLECTION);
        mongo.updateMulti(
                Query.query(
                        Criteria.where("state")
                                .is("RUNNING")
                                .and("lease_until")
                                .lte(date(now))
                                .and("active_key")
                                .exists(false)),
                new Update().set("state", "PENDING").set("retry_at", date(now)),
                COLLECTION);
        mongo.updateMulti(
                Query.query(
                        Criteria.where("state").is("PENDING").and("attempts").gte(MAX_ATTEMPTS)),
                new Update().set("state", "FAILED").set("error_kind", "RetryLimitReached"),
                COLLECTION);
        Query pending =
                Query.query(
                                Criteria.where("state")
                                        .is("PENDING")
                                        .and("retry_at")
                                        .lte(date(now))
                                        .and("attempts")
                                        .lt(MAX_ATTEMPTS))
                        .with(Sort.by(Sort.Direction.ASC, "retry_at", "_id"));
        return mongo.findAndModify(
                pending,
                new Update()
                        .set("state", "RUNNING")
                        .set("lease_owner", UUID.randomUUID().toString())
                        .set("lease_until", date(now.plusSeconds(120)))
                        .inc("attempts", 1),
                FindAndModifyOptions.options().returnNew(true),
                Document.class,
                COLLECTION);
    }

    /** CAS/lease ownership is checked before provider I/O. */
    public boolean startProvider(Document job, String key) {
        return mongo.updateFirst(
                                owned(job),
                                new Update()
                                        .set("active_key", key)
                                        .set("lease_until", date(now().plusSeconds(120))),
                                COLLECTION)
                        .getMatchedCount()
                == 1;
    }

    public void acknowledge(Document job, String key) {
        if (mongo.updateFirst(
                                owned(job),
                                new Update()
                                        .addToSet("delivered", key)
                                        .unset("active_key")
                                        .set("lease_until", date(now().plusSeconds(120))),
                                COLLECTION)
                        .getMatchedCount()
                != 1) throw new IllegalStateException("Delivery acknowledgement lease was lost");
    }

    public void complete(Document job) {
        mutate(job, new Update().set("state", "COMPLETED").unset("active_key"));
    }

    public void retry(Document job, String safeErrorKind) {
        int attempts = job.getInteger("attempts");
        long delay = Math.min(300, 5L << Math.max(0, attempts - 1));
        mutate(
                job,
                new Update()
                        .set("state", attempts >= MAX_ATTEMPTS ? "FAILED" : "PENDING")
                        .set("retry_at", date(now().plusSeconds(delay)))
                        .set("error_kind", safeErrorKind)
                        .unset("active_key"));
    }

    public void uncertain(Document job, String safeErrorKind) {
        mutate(job, new Update().set("state", "UNCERTAIN").set("error_kind", safeErrorKind));
    }

    private void mutate(Document job, Update update) {
        if (mongo.updateFirst(owned(job), update, COLLECTION).getMatchedCount() != 1)
            throw new IllegalStateException("Delivery intent lease was lost");
    }

    private Query owned(Document job) {
        return Query.query(
                Criteria.where("_id")
                        .is(job.getString("_id"))
                        .and("state")
                        .is("RUNNING")
                        .and("lease_owner")
                        .is(job.getString("lease_owner"))
                        .and("lease_until")
                        .gt(date(now())));
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static Date date(Instant instant) {
        return Date.from(instant);
    }
}
