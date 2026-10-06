package ru.rutcampustrack.attendance.audit;

import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Trusted existing event transport only. No public ingestion operation. */
@Service
public class ActionEventHandler {
    private static final Set<String> SCHEDULE = Set.of("LESSON_CREATED", "LESSON_EDITED", "LESSON_CANCELLED", "LESSON_RESTORED", "LESSON_TRANSFERRED", "ROOM_CHANGED");
    private static final Set<String> HOMEWORK = Set.of("HOMEWORK_CREATED", "HOMEWORK_EDITED", "HOMEWORK_ARCHIVED");
    private static final Set<String> REQUIRED = Set.of("group_id", "semester_id", "lesson_id", "action", "actor", "student", "before", "after", "geo", "ticket", "homework", "revision");
    private final MongoTemplate mongo;
    private final LessonActionService actions;
    public ActionEventHandler(MongoTemplate mongo, LessonActionService actions) { this.mongo = mongo; this.actions = actions; }

    public void validate(Map<String, Object> envelope) { parse(envelope); }

    public void accept(Map<String, Object> envelope) {
        ValidatedEvent parsed = parse(envelope);
        String id = parsed.id(); Instant at = parsed.at(); String source = parsed.source();
        Map<String, Object> p = parsed.payload();
        LessonActionDocument prior = mongo.findById(id, LessonActionDocument.class);
        if (prior != null) {
            if (!source.equals(prior.getSource()) || !at.equals(prior.getOccurredAt()) || !p.equals(prior.getPayload())) throw invalid();
            return; // permanent receipt remains authoritative after inbox cleanup
        }
        actions.append(id, at, source, p);
    }

    private record ValidatedEvent(String id, Instant at, String source, Map<String, Object> payload) { }

    @SuppressWarnings("unchecked")
    private static ValidatedEvent parse(Map<String, Object> envelope) {
        String type = (String) envelope.get("event_type");
        boolean schedule = "schedule.action-recorded".equals(type);
        if (!schedule && !"homework.action-recorded".equals(type)) throw invalid();
        String source = schedule ? "schedule-service" : "academic-service";
        if (!source.equals(envelope.get("source")) || !(envelope.get("event_version") instanceof Number v)
                || v.doubleValue() != 1 || !(envelope.get("event_id") instanceof String id)
                || !id.matches("[A-Za-z0-9_-]{1,128}")
                || !(envelope.get("trace_id") instanceof String trace) || trace.isBlank()
                || !(envelope.get("payload") instanceof Map<?, ?> raw)) throw invalid();
        Instant at;
        try { at = Instant.parse((String) envelope.get("occurred_at")).truncatedTo(java.time.temporal.ChronoUnit.MILLIS); } catch (RuntimeException malformed) { throw invalid(); }
        Map<String, Object> p = (Map<String, Object>) raw;
        if (!p.keySet().containsAll(REQUIRED) || p.keySet().stream().anyMatch(key -> !REQUIRED.contains(key) && !"homework_id".equals(key))
                || !positive(p.get("group_id")) || !positive(p.get("semester_id"))
                || p.get("lesson_id") != null && !positive(p.get("lesson_id"))
                || schedule && p.get("lesson_id") == null || !(p.get("action") instanceof String action)
                || !(schedule ? SCHEDULE : HOMEWORK).contains(action)) throw invalid();
        validatePerson(p.get("actor")); validatePerson(p.get("student"));
        if (p.get("homework_id") != null && !positive(p.get("homework_id"))
                || p.get("revision") != null && !(p.get("revision") instanceof String) && !positive(p.get("revision"))) throw invalid();
        if (p.get("homework") instanceof Map<?, ?> homework && (!positive(homework.get("id"))
                || !homework.containsKey("revision") || homework.get("revision") != null && !positive(homework.get("revision"))
                || homework.keySet().stream().anyMatch(key -> !Set.of("id", "revision").contains(key)))) throw invalid();
        for (String key : Set.of("before", "after", "geo", "ticket", "homework")) {
            if (p.get(key) != null && !(p.get(key) instanceof Map<?, ?>)) throw invalid();
        }
        if (p.get("before") == null && p.get("after") == null) throw invalid();
        // Scope is checked against the authoritative immutable SQL snapshot, not a
        // later current lesson which may have been deleted before message delivery.
        scopeSnapshot(p.get("before"), p, schedule); scopeSnapshot(p.get("after"), p, schedule);
        if (!schedule && p.get("lesson_id") != null) {
            long lesson = ((Number) p.get("lesson_id")).longValue();
            boolean linked = java.util.stream.Stream.of(p.get("before"), p.get("after"))
                    .filter(Map.class::isInstance).map(value -> ((Map<?, ?>) value).get("lesson_id"))
                    .anyMatch(value -> positive(value) && ((Number) value).longValue() == lesson);
            if (!linked) throw invalid();
        }
        rejectPrivateTransport(p);
        return new ValidatedEvent(id, at, source, p);
    }

    private static void validatePerson(Object value) {
        if (value == null) return;
        if (!(value instanceof Map<?, ?> p) || !positive(p.get("user_id"))
                || p.keySet().stream().anyMatch(key -> !Set.of("user_id", "role", "display_name").contains(key))
                || p.get("role") != null && !(p.get("role") instanceof String)
                || p.get("display_name") != null && !(p.get("display_name") instanceof String)) throw invalid();
    }
    private static void scopeSnapshot(Object value, Map<String, Object> p, boolean schedule) {
        if (!(value instanceof Map<?, ?> snapshot)) return;
        for (String key : Set.of("group_id", "semester_id", "lesson_id")) {
            if ((schedule || !"lesson_id".equals(key)) && !snapshot.containsKey(key)) throw invalid();
            Object scope = snapshot.get(key), expected = p.get(key);
            if (!"lesson_id".equals(key) && !positive(scope)) throw invalid();
            if ((schedule || !"lesson_id".equals(key)) && scope != null && expected != null
                    && (!positive(scope) || ((Number) scope).longValue() != ((Number) expected).longValue())) throw invalid();
        }
    }
    private static boolean positive(Object value) {
        return value instanceof Number n && n.longValue() > 0 && n.doubleValue() == n.longValue();
    }
    private static void rejectPrivateTransport(Object value) {
        if (value instanceof Map<?, ?> map) map.forEach((key, nested) -> {
            if (Set.of("latitude", "longitude", "lat", "lng", "download_url", "public_url", "file_bytes", "access_token").contains(key.toString().toLowerCase(java.util.Locale.ROOT))) throw invalid();
            rejectPrivateTransport(nested);
        });
        else if (value instanceof Iterable<?> list) list.forEach(ActionEventHandler::rejectPrivateTransport);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid authoritative action event"); }
}
