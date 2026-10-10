package ru.rutcampustrack.notification.history;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.reminder.ReminderAttendanceStateService;

import java.time.Instant;
import java.util.*;

/** A minimal immutable recipient snapshot is shared by history and queued WebPush. */
@Service
public class NotificationAudienceService {
    public static final String COLLECTION = "notification_event_audiences";
    private final MongoTemplate mongo;
    private final NotificationAudienceClient client;
    private final ReminderAttendanceStateService reminderState;
    public NotificationAudienceService(MongoTemplate mongo, NotificationAudienceClient client,
                                       ReminderAttendanceStateService reminderState) {
        this.mongo = mongo; this.client = client; this.reminderState = reminderState;
    }

    /** Called before the transactional event claim commits; unavailable authority rolls everything back. */
    public Map<Long, Set<String>> snapshot(Map<String, Object> envelope) {
        var request = NotificationAudienceClient.request(envelope);
        String contextHash = java.util.HexFormat.of().formatHex(digest(NotificationAudienceClient.contextBytes(envelope)));
        Document stored = mongo.findById(request.getRequestId(), Document.class, COLLECTION);
        if (stored == null) {
            Map<Long, Set<String>> admitted = filter(envelope, client.resolve(envelope));
            Document roles = new Document();
            admitted.forEach((id, values) -> roles.put(Long.toString(id), new ArrayList<>(values)));
            stored = mongo.findAndModify(Query.query(Criteria.where("_id").is(request.getRequestId())),
                    new Update().setOnInsert("event_type", request.getEventType()).setOnInsert("context_hash", contextHash)
                            .setOnInsert("roles", roles).setOnInsert("created_at", Date.from(Instant.now())),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), Document.class, COLLECTION);
        }
        if (stored == null || !request.getEventType().equals(stored.getString("event_type"))
                || !contextHash.equals(stored.getString("context_hash"))) throw new IllegalStateException("Audience snapshot scope is invalid");
        var decoded = decode(stored);
        var allowed = NotificationCatalog.meaning(NotificationCatalog.eventType(request.getEventType(), NotificationAudienceClient.payload(envelope)).orElseThrow()).roles();
        if (decoded.values().stream().anyMatch(roles -> !allowed.containsAll(roles))) throw new IllegalStateException("Stored audience roles are invalid");
        return decoded;
    }

    /** Fresh live grants/capabilities can only narrow the original recipients, never create a new role view. */
    public Map<Long, Set<String>> current(Map<String, Object> envelope, Map<Long, Set<String>> original) {
        return intersect(original, filter(envelope, client.resolve(envelope)));
    }

    static Map<Long, Set<String>> intersect(Map<Long, Set<String>> original, Map<Long, Set<String>> live) {
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        original.forEach((id, roles) -> {
            Set<String> retained = new LinkedHashSet<>(roles);
            retained.retainAll(live.getOrDefault(id, Set.of()));
            if (!retained.isEmpty()) result.put(id, Set.copyOf(retained));
        });
        return Collections.unmodifiableMap(result);
    }

    private Map<Long, Set<String>> filter(Map<String, Object> envelope, Map<Long, Set<String>> recipients) {
        Map<String, Object> payload = NotificationAudienceClient.payload(envelope);
        String eventType = (String) envelope.get("event_type");
        Map<Long, Set<String>> result = new LinkedHashMap<>(recipients);
        if ("AUTOMATIC_GROUP_CYCLE".equals(payload.get("notification_cause"))
                && Set.of("group.renamed", "group.archived").contains(eventType)) {
            result.replaceAll((id, roles) -> {
                Set<String> filtered = new LinkedHashSet<>(roles); filtered.remove("ADMIN"); return Set.copyOf(filtered);
            });
            result.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        if ("lesson.reminder".equals(eventType)) {
            Set<Long> unmarked = reminderState.getUnmarkedUserIds(NotificationAudienceClient.positive(payload.get("lesson_id")), result.keySet());
            result.keySet().retainAll(unmarked);
        }
        return Collections.unmodifiableMap(result);
    }

    public static Map<Long, Set<String>> decode(Document snapshot) {
        if (!(snapshot.get("roles") instanceof Map<?, ?> values)) throw new IllegalStateException("Audience snapshot is invalid");
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            long id;
            try { id = Long.parseLong(key.toString()); } catch (RuntimeException error) { throw new IllegalStateException("Audience snapshot ID is invalid"); }
            if (id <= 0 || !Long.toString(id).equals(key.toString()) || result.containsKey(id)
                    || !(value instanceof List<?> roles) || roles.isEmpty()) throw new IllegalStateException("Audience snapshot roles are invalid");
            Set<String> roleSet = new LinkedHashSet<>();
            for (Object role : roles) {
                if (!(role instanceof String name) || !Set.of("STUDENT", "HEADMAN", "TEACHER", "ADMIN").contains(name) || !roleSet.add(name))
                    throw new IllegalStateException("Audience snapshot roles are invalid");
            }
            result.put(id, Set.copyOf(roleSet));
        });
        return Collections.unmodifiableMap(result);
    }
    private static byte[] digest(byte[] bytes) {
        try { return java.security.MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
