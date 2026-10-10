package ru.rutcampustrack.attendance.audit;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import ru.rutcampustrack.shared.outbox.OutboxStorage;
import ru.rutcampustrack.attendance.event.EventEnvelope;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.checkin.AttendanceRevisions;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LessonActionService {
    private final MongoTemplate mongo;
    private final Clock clock;
    private final OutboxStorage outbox;
    private final ObjectMapper mapper;
    public LessonActionService(MongoTemplate mongo, Clock clock, OutboxStorage outbox, ObjectMapper mapper) {
        this.mongo = mongo; this.clock = clock; this.outbox = outbox; this.mapper = mapper;
    }

    public void mark(String action, AttendanceDocument before, AttendanceDocument after,
                     Long actorId, String actorRole, Map<String, Object> ticket) {
        if (before == null && after == null || AttendanceRevisions.sameMeaning(before, after)) return;
        AttendanceDocument scope = after == null ? before : after;
        Map<String, Object> beforeSnapshot = snapshot(before);
        if (beforeSnapshot != null && before != null && before.getAttachmentId() != null
                && (after == null || !java.util.Objects.equals(before.getAttachmentId(), after.getAttachmentId()))) {
            @SuppressWarnings("unchecked") Map<String, Object> file = (Map<String, Object>) beforeSnapshot.get("attachment");
            file.put("availability", "UNAVAILABLE");
        }
        append(UUID.randomUUID().toString(), clock.instant(), "attendance-service", payload(
                scope.getGroupId(), scope.getSemesterId(), scope.getLessonId(), action,
                person(actorId, actorRole, null), person(scope.getUserId(), "STUDENT", null),
                beforeSnapshot, snapshot(after), null, ticket, null,
                AttendanceRevisions.effective(after)));
    }

    public void local(Long groupId, Long semesterId, Long lessonId, String action,
                      Map<String, Object> actor, Map<String, Object> student, Map<String, Object> before,
                      Map<String, Object> after, Map<String, Object> geo, Map<String, Object> ticket, Instant at) {
        append(UUID.randomUUID().toString(), at, "attendance-service", payload(groupId, semesterId,
                lessonId, action, actor, student, before, after, geo, ticket, null, null));
    }

    public void append(String id, Instant at, String source, Map<String, Object> payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Lesson action must share the domain Mongo transaction");
        }
        LessonActionDocument row = new LessonActionDocument();
        row.setId(id); row.setOccurredAt(at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)); row.setSource(source); row.setPayload(payload);
        row.setGroupId(((Number) payload.get("group_id")).longValue());
        row.setSemesterId(((Number) payload.get("semester_id")).longValue());
        Object lessonId = payload.get("lesson_id");
        row.setLessonId(lessonId == null ? null : ((Number) lessonId).longValue());
        mongo.insert(row);
        // Rejected attempts are retained audit evidence and never change dashboard aggregates.
        if ("REJECTED".equals(payload.get("outcome"))) return;
        // Projection and signal share the same Mongo transaction: REST is readable before delivery.
        try {
            outbox.save("dashboard.invalidated", mapper.writeValueAsString(
                    EventEnvelope.build("dashboard.invalidated", Map.of())));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Failed to serialize dashboard invalidation", failure);
        }
    }

    public static Map<String, Object> person(Long id, String role, String name) {
        if (id == null || id <= 0) return null;
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("user_id", id); person.put("role", role); person.put("display_name", name);
        return person;
    }

    public static Map<String, Object> payload(Long group, Long semester, Long lesson, String action,
                                            Map<String, Object> actor, Map<String, Object> student,
                                            Map<String, Object> before, Map<String, Object> after,
                                            Map<String, Object> geo, Map<String, Object> ticket,
                                            Map<String, Object> homework, Object revision) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("group_id", group); p.put("semester_id", semester); p.put("lesson_id", lesson);
        p.put("action", action); p.put("actor", actor); p.put("student", student);
        p.put("before", before); p.put("after", after); p.put("geo", geo);
        p.put("ticket", ticket); p.put("homework", homework); p.put("revision", revision);
        return p;
    }

    public static Map<String, Object> snapshot(AttendanceDocument d) {
        if (d == null) return null;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("status", d.getStatus() == null ? null : d.getStatus().name());
        p.put("source", d.getSource() == null ? null : d.getSource().name());
        p.put("marked_by", d.getMarkedBy()); p.put("mark_revision", AttendanceRevisions.effective(d));
        p.put("excuse_reason", d.getExcuseReason()); p.put("excuse_type", d.getExcuseType() == null ? null : d.getExcuseType().name());
        p.put("excuse_comment", d.getExcuseComment());
        if (d.getAttachmentId() != null) {
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("id", d.getAttachmentId()); file.put("name", d.getAttachmentName());
            file.put("content_type", d.getAttachmentContentType()); file.put("size", d.getAttachmentSize());
            // Historical metadata never promises that deleted bytes can still be downloaded.
            file.put("availability", "CHECK_CURRENT_ATTACHMENT_API"); p.put("attachment", file);
        }
        return p;
    }
}
