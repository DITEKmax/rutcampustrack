package ru.rutcampustrack.attendance.event;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Strict v1 recurring / v2 one-off transfer parser. Bindings are validated but owned by Academic. */
public record LessonTransferRequestedEvent(
        int eventVersion,
        String operationId,
        String requestKey,
        long actorId,
        long groupId,
        long semesterId,
        long occurrenceId,
        String transferPayloadHash,
        long transferRevision,
        Snapshot source,
        Snapshot target,
        int batchIndex,
        int batchCount) {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public static LessonTransferRequestedEvent parse(Map<String, Object> envelope) {
        if (envelope == null
                || !"lesson.transfer.requested".equals(envelope.get("event_type"))
                || !"schedule-service".equals(envelope.get("source"))) {
            throw invalid("unsupported or untrusted lesson.transfer.requested envelope");
        }
        long version = integer(envelope.get("event_version"), "event_version");
        if (version != 1 && version != 2) throw invalid("unsupported lesson transfer version");
        uuid(envelope.get("event_id"), "event_id");
        string(envelope.get("trace_id"), "trace_id");
        try {
            OffsetDateTime.parse(string(envelope.get("occurred_at"), "occurred_at"));
        } catch (RuntimeException error) {
            throw invalid("occurred_at must be an ISO timestamp");
        }
        Map<String, Object> payload = object(envelope.get("payload"), "payload");
        String operationId = uuid(payload.get("operation_id"), "operation_id");
        String requestKey = uuid(payload.get("request_key"), "request_key");
        long actorId = positive(payload.get("actor_id"), "actor_id");
        long groupId = positive(payload.get("group_id"), "group_id");
        long semesterId = positive(payload.get("semester_id"), "semester_id");
        long occurrenceId = positive(payload.get("occurrence_id"), "occurrence_id");
        String payloadHash = hash(payload.get("transfer_payload_hash"), "transfer_payload_hash");
        long transferRevision = positive(payload.get("transfer_revision"), "transfer_revision");
        Snapshot source = snapshot(payload.get("source"), "source", (int) version);
        Snapshot target = snapshot(payload.get("target"), "target", (int) version);
        if (source.lessonId() == target.lessonId()) {
            throw invalid("source and target lesson ids must differ");
        }
        if (version == 1 && !source.scheduleItemId().equals(target.scheduleItemId())) {
            throw invalid("v1 source and target must have the same recurring origin");
        }
        if (version == 2) {
            Map<String, Object> sourceObject = object(payload.get("source"), "source");
            Map<String, Object> targetObject = object(payload.get("target"), "target");
            if (!source.oneOffLessonId().equals(target.oneOffLessonId())
                    || !source.assignmentId().equals(target.assignmentId())
                    || !source.subjectId().equals(target.subjectId())
                    || !source.assignedTeacherId().equals(target.assignedTeacherId())
                    || !source.lessonType().equals(target.lessonType())
                    || positive(payload.get("source_lesson_id"), "source_lesson_id") != source.lessonId()
                    || positive(payload.get("target_lesson_id"), "target_lesson_id") != target.lessonId()
                    || positive(sourceObject.get("occurrence_id"), "source.occurrence_id") != occurrenceId
                    || positive(targetObject.get("occurrence_id"), "target.occurrence_id") != occurrenceId
                    || positive(sourceObject.get("group_id"), "source.group_id") != groupId
                    || positive(targetObject.get("group_id"), "target.group_id") != groupId
                    || positive(sourceObject.get("semester_id"), "source.semester_id") != semesterId
                    || positive(targetObject.get("semester_id"), "target.semester_id") != semesterId
                    || transferRevision != source.occurrenceRevision() + 1
                    || target.occurrenceRevision() != transferRevision
                    || target.generation() != source.generation() + 1 || target.lessonRevision() != 1) {
                throw invalid("v2 lesson transfer has inconsistent physical snapshots or scope");
            }
        }
        int batchIndex = nonNegativeInt(payload.get("batch_index"), "batch_index");
        int batchCount = positiveInt(payload.get("batch_count"), "batch_count");
        if (batchIndex >= batchCount) {
            throw invalid("batch_index must be less than batch_count");
        }
        List<?> bindings = validateBindings(payload.get("bindings"));
        if (bindings.isEmpty() && (batchIndex != 0 || batchCount != 1)) {
            throw invalid("an empty bindings batch must be the only batch");
        }
        return new LessonTransferRequestedEvent((int) version, operationId, requestKey, actorId, groupId,
                semesterId, occurrenceId, payloadHash, transferRevision,
                source, target, batchIndex, batchCount);
    }

    public boolean sameOperationIdentity(LessonTransferRequestedEvent other) {
        return other != null
                && eventVersion == other.eventVersion && operationId.equals(other.operationId)
                && requestKey.equals(other.requestKey)
                && actorId == other.actorId
                && groupId == other.groupId
                && semesterId == other.semesterId
                && occurrenceId == other.occurrenceId
                && transferPayloadHash.equalsIgnoreCase(other.transferPayloadHash)
                && transferRevision == other.transferRevision
                && source.equals(other.source)
                && target.equals(other.target);
    }

    private static Snapshot snapshot(Object raw, String field, int version) {
        Map<String, Object> object = object(raw, field);
        LocalDate date;
        LocalTime start;
        LocalTime end;
        try {
            date = LocalDate.parse(string(object.get("date"), field + ".date"));
            start = LocalTime.parse(string(object.get("start_time"), field + ".start_time"));
            end = LocalTime.parse(string(object.get("end_time"), field + ".end_time"));
        } catch (RuntimeException error) {
            throw invalid(field + " contains an invalid date or time");
        }
        Object rawRoom = object.get("room");
        if (rawRoom != null && !(rawRoom instanceof String)) {
            throw invalid(field + ".room must be a string or null");
        }
        Long scheduleItemId = null;
        Long oneOffLessonId = null;
        Long assignmentId = null;
        Long subjectId = null;
        Long teacherId = null;
        String lessonType = null;
        if (version == 1) {
            scheduleItemId = positive(object.get("schedule_item_id"), field + ".schedule_item_id");
            if (object.get("one_off_lesson_id") != null) throw invalid("v1 requires a recurring origin only");
        } else {
            if (!object.containsKey("schedule_item_id") || object.get("schedule_item_id") != null) {
                throw invalid("v2 requires explicit null schedule_item_id");
            }
            oneOffLessonId = positive(object.get("one_off_lesson_id"), field + ".one_off_lesson_id");
            assignmentId = positive(object.get("assignment_id"), field + ".assignment_id");
            subjectId = positive(object.get("subject_id"), field + ".subject_id");
            teacherId = positive(object.get("assigned_teacher_id"), field + ".assigned_teacher_id");
            lessonType = string(object.get("lesson_type"), field + ".lesson_type");
            if (!List.of("lecture", "practice", "lab").contains(lessonType)
                    || !"planned".equals(object.get("status")) || !end.isAfter(start)
                    || positiveInt(object.get("lesson_number"), field + ".lesson_number") > 8
                    || integer(object.get("day_of_week"), field + ".day_of_week") != date.getDayOfWeek().getValue()
                    || !"all".equals(object.get("week_type_snapshot"))) {
                throw invalid("v2 one-off snapshot has invalid type, status or immutable slot");
            }
        }
        return new Snapshot(
                positive(object.get("lesson_id"), field + ".lesson_id"),
                scheduleItemId, oneOffLessonId, assignmentId, subjectId, teacherId, lessonType,
                positive(object.get("generation"), field + ".generation"),
                positive(object.get("lesson_revision"), field + ".lesson_revision"),
                positive(object.get("occurrence_revision"), field + ".occurrence_revision"),
                date,
                positiveInt(object.get("lesson_number"), field + ".lesson_number"),
                start,
                end,
                (String) rawRoom);
    }

    private static List<?> validateBindings(Object raw) {
        if (!(raw instanceof List<?> bindings)) {
            throw invalid("bindings must be an array");
        }
        if (bindings.size() > 64) throw invalid("bindings batch exceeds 64 items");
        for (Object item : bindings) {
            Map<String, Object> binding = object(item, "bindings[]");
            positive(binding.get("binding_id"), "bindings[].binding_id");
            Object homeworkId = binding.get("homework_id");
            if (homeworkId != null) positive(homeworkId, "bindings[].homework_id");
            String state = string(binding.get("state"), "bindings[].state");
            if (!"PENDING".equals(state) && !"ACTIVE".equals(state)) {
                throw invalid("bindings[].state is unsupported");
            }
            nonNegative(binding.get("revision"), "bindings[].revision");
            positive(binding.get("actor_id"), "bindings[].actor_id");
            uuid(binding.get("request_key"), "bindings[].request_key");
            hash(binding.get("payload_hash"), "bindings[].payload_hash");
        }
        return bindings;
    }

    private static long positive(Object value, String field) {
        long parsed = integer(value, field);
        if (parsed <= 0) throw invalid(field + " must be positive");
        return parsed;
    }

    private static int positiveInt(Object value, String field) {
        long parsed = positive(value, field);
        if (parsed > Integer.MAX_VALUE) throw invalid(field + " is out of range");
        return (int) parsed;
    }

    private static int nonNegativeInt(Object value, String field) {
        long parsed = integer(value, field);
        if (parsed < 0 || parsed > Integer.MAX_VALUE) throw invalid(field + " is out of range");
        return (int) parsed;
    }

    private static long integer(Object value, String field) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger big && big.bitLength() < 64) {
            return big.longValue();
        }
        throw invalid(field + " must be an integer");
    }

    private static long nonNegative(Object value, String field) {
        long parsed = integer(value, field);
        if (parsed < 0) throw invalid(field + " must be non-negative");
        return parsed;
    }

    private static String uuid(Object value, String field) {
        String parsed = string(value, field);
        if (!UUID_TEXT.matcher(parsed).matches()) throw invalid(field + " must be a UUID");
        try {
            return UUID.fromString(parsed).toString();
        } catch (IllegalArgumentException error) {
            throw invalid(field + " must be a UUID");
        }
    }

    private static String hash(Object value, String field) {
        String parsed = string(value, field);
        if (!SHA256.matcher(parsed).matches()) throw invalid(field + " must be a SHA-256 hex string");
        return parsed.toLowerCase(java.util.Locale.ROOT);
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String parsed) || parsed.isBlank()) throw invalid(field + " must be a string");
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)
                || map.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw invalid(field + " must be an object");
        }
        return (Map<String, Object>) map;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record Snapshot(long lessonId, Long scheduleItemId, Long oneOffLessonId, Long assignmentId,
                           Long subjectId, Long assignedTeacherId, String lessonType, long generation,
                           long lessonRevision, long occurrenceRevision, LocalDate date,
                           int lessonNumber, LocalTime startTime, LocalTime endTime, String room) {}
}
