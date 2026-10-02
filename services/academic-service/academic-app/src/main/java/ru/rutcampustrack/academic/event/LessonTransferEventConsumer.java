package ru.rutcampustrack.academic.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.homework.HomeworkBindingTransferCoordinator;
import ru.rutcampustrack.academic.homework.LessonTransferBatch;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Receives immutable Schedule transfer batches; the coordinator commits each batch with its ack outbox. */
@Component
@Slf4j
public class LessonTransferEventConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "academic-lesson-transfer";
    private static final String EVENT_TYPE = "lesson.transfer.requested";
    private static final String EVENT_SOURCE = "schedule-service";

    private final HomeworkBindingTransferCoordinator transferCoordinator;
    private final IdempotencyGuard idempotencyGuard;

    public LessonTransferEventConsumer(HomeworkBindingTransferCoordinator transferCoordinator,
                                       IdempotencyGuard idempotencyGuard) {
        this.transferCoordinator = transferCoordinator;
        this.idempotencyGuard = idempotencyGuard;
    }

    @RabbitListener(queues = RabbitConfig.LESSON_TRANSFER_EVENTS_QUEUE)
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        if (envelope == null || !EVENT_TYPE.equals(envelope.get("event_type"))) return;
        LessonTransferBatch batch = parseTransferBatch(envelope);
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) return;
        withTraceContext(envelope, () -> transferCoordinator.applyBatch(batch));
    }

    private static LessonTransferBatch parseTransferBatch(Map<String, Object> envelope) {
        if (!EVENT_SOURCE.equals(envelope.get("source"))) {
            throw new IllegalArgumentException("lesson.transfer.requested has an untrusted source");
        }
        long version = positiveLong(envelope.get("event_version"), "event_version");
        if (version != 1 && version != 2) {
            throw new IllegalArgumentException("lesson.transfer.requested has an unsupported version");
        }
        if (!(envelope.get("payload") instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("lesson.transfer.requested has no payload object");
        }

        UUID eventId = uuid(envelope.get("event_id"), "event_id");
        UUID operationId = uuid(payload.get("operation_id"), "operation_id");
        UUID requestKey = uuid(payload.get("request_key"), "request_key");
        long actorId = positiveLong(payload.get("actor_id"), "actor_id");
        String operationHash = hash(payload.get("transfer_payload_hash"), "transfer_payload_hash");
        long occurrenceId = positiveLong(payload.get("occurrence_id"), "occurrence_id");
        long groupId = positiveLong(payload.get("group_id"), "group_id");
        long semesterId = positiveLong(payload.get("semester_id"), "semester_id");
        long transferRevision = positiveLong(payload.get("transfer_revision"), "transfer_revision");
        long sourceLessonId = positiveLong(payload.get("source_lesson_id"), "source_lesson_id");
        long targetLessonId = positiveLong(payload.get("target_lesson_id"), "target_lesson_id");
        Map<?, ?> source = object(payload.get("source"), "source");
        Map<?, ?> target = object(payload.get("target"), "target");
        Long oneOffId = validateOrigin(version, source, target);

        long sourceSnapshotId = positiveLong(source.get("lesson_id"), "source.lesson_id");
        long targetSnapshotId = positiveLong(target.get("lesson_id"), "target.lesson_id");
        if (sourceLessonId != sourceSnapshotId || targetLessonId != targetSnapshotId
                || sourceLessonId == targetLessonId
                || positiveLong(source.get("occurrence_id"), "source.occurrence_id") != occurrenceId
                || positiveLong(target.get("occurrence_id"), "target.occurrence_id") != occurrenceId
                || positiveLong(source.get("group_id"), "source.group_id") != groupId
                || positiveLong(target.get("group_id"), "target.group_id") != groupId
                || positiveLong(source.get("semester_id"), "source.semester_id") != semesterId
                || positiveLong(target.get("semester_id"), "target.semester_id") != semesterId
                || positiveLong(source.get("assignment_id"), "source.assignment_id")
                    != positiveLong(target.get("assignment_id"), "target.assignment_id")
                || positiveLong(source.get("subject_id"), "source.subject_id")
                    != positiveLong(target.get("subject_id"), "target.subject_id")
                || !"planned".equals(source.get("status"))
                || !"planned".equals(target.get("status"))) {
            throw new IllegalArgumentException("lesson.transfer.requested has inconsistent source/target snapshots");
        }
        long sourceGeneration = positiveLong(source.get("generation"), "source.generation");
        long targetGeneration = positiveLong(target.get("generation"), "target.generation");
        long expectedRevision = positiveLong(source.get("occurrence_revision"), "source.occurrence_revision");
        if (transferRevision != expectedRevision + 1
                || positiveLong(target.get("occurrence_revision"), "target.occurrence_revision") != transferRevision
                || targetGeneration != sourceGeneration + 1
                || positiveLong(target.get("lesson_revision"), "target.lesson_revision") != 1
                || positiveLong(source.get("lesson_revision"), "source.lesson_revision") <= 0) {
            throw new IllegalArgumentException("lesson.transfer.requested has an invalid target generation");
        }
        LocalDate sourceDate = date(source.get("date"), "source.date");
        LocalDate targetDate = date(target.get("date"), "target.date");
        int sourceNumber = lessonNumber(source.get("lesson_number"), "source.lesson_number");
        int targetNumber = lessonNumber(target.get("lesson_number"), "target.lesson_number");

        int batchIndex = exactInt(payload.get("batch_index"), "batch_index");
        int batchCount = exactInt(payload.get("batch_count"), "batch_count");
        if (batchCount < 1 || batchIndex < 0 || batchIndex >= batchCount) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid batch bounds");
        }
        Object rawBindings = payload.get("bindings");
        if (!(rawBindings instanceof List<?> values) || values.size() > 64) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid bindings batch");
        }
        if (values.isEmpty() && (batchCount != 1 || batchIndex != 0)) {
            throw new IllegalArgumentException("empty lesson transfer bindings require one explicit batch");
        }

        List<LessonTransferBatch.Binding> bindings = new ArrayList<>(values.size());
        long previousBindingId = 0;
        for (Object raw : values) {
            Map<?, ?> binding = object(raw, "binding");
            long bindingId = positiveLong(binding.get("binding_id"), "binding.binding_id");
            long bindingActorId = positiveLong(binding.get("actor_id"), "binding.actor_id");
            UUID bindingRequestKey = uuid(binding.get("request_key"), "binding.request_key");
            Object rawHomeworkId = binding.get("homework_id");
            Long homeworkId = rawHomeworkId == null ? null
                    : positiveLong(rawHomeworkId, "binding.homework_id");
            String bindingPayloadHash = hash(binding.get("payload_hash"), "binding.payload_hash");
            Object rawState = binding.get("state");
            if (!(rawState instanceof String state) || !List.of("PENDING", "ACTIVE").contains(state)) {
                throw new IllegalArgumentException("lesson.transfer.requested has invalid binding.state");
            }
            long revision = positiveLong(binding.get("revision"), "binding.revision");
            if (bindingId <= previousBindingId) {
                throw new IllegalArgumentException("lesson.transfer.requested bindings are not strictly ordered");
            }
            previousBindingId = bindingId;
            bindings.add(new LessonTransferBatch.Binding(bindingId, bindingActorId,
                    bindingRequestKey, homeworkId, bindingPayloadHash, state, revision));
        }
        // V1 receipts retain their accepted binding hash. V2 binds the explicit
        // origin/version to the same durable duplicate-batch comparison.
        String batchHash = version == 1 ? hashBindings(bindings)
                : hashOneOffBatch(payload, source, target, oneOffId, hashBindings(bindings));
        return new LessonTransferBatch(eventId, operationId, requestKey, actorId, operationHash,
                occurrenceId, groupId, positiveLong(source.get("subject_id"), "source.subject_id"),
                semesterId, expectedRevision, sourceLessonId, targetLessonId,
                sourceDate, sourceNumber, targetDate, targetNumber,
                batchIndex, batchCount, bindings, batchHash);
    }

    private static String hashBindings(List<LessonTransferBatch.Binding> bindings) {
        StringBuilder canonical = new StringBuilder();
        for (LessonTransferBatch.Binding binding : bindings) {
            canonical.append(binding.bindingId()).append('|')
                    .append(binding.actorId()).append('|')
                    .append(binding.requestKey()).append('|')
                    .append(binding.homeworkId() == null ? "-" : binding.homeworkId()).append('|')
                    .append(binding.payloadHash()).append('|')
                    .append(binding.state()).append('|')
                    .append(binding.revision()).append('\n');
        }
        return hashText(canonical.toString());
    }

    private static String hashText(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Long validateOrigin(long version, Map<?, ?> source, Map<?, ?> target) {
        if (version == 1) {
            if (source.get("one_off_lesson_id") != null || target.get("one_off_lesson_id") != null
                    || positiveLong(source.get("schedule_item_id"), "source.schedule_item_id")
                        != positiveLong(target.get("schedule_item_id"), "target.schedule_item_id")) {
                throw new IllegalArgumentException("v1 lesson transfer requires the same recurring origin");
            }
            return null;
        }
        if (!source.containsKey("schedule_item_id") || !target.containsKey("schedule_item_id")
                || source.get("schedule_item_id") != null || target.get("schedule_item_id") != null) {
            throw new IllegalArgumentException("v2 lesson transfer requires explicit null recurring origins");
        }
        long origin = positiveLong(source.get("one_off_lesson_id"), "source.one_off_lesson_id");
        if (origin != positiveLong(target.get("one_off_lesson_id"), "target.one_off_lesson_id")
                || positiveLong(source.get("assigned_teacher_id"), "source.assigned_teacher_id")
                    != positiveLong(target.get("assigned_teacher_id"), "target.assigned_teacher_id")
                || !(source.get("lesson_type") instanceof String type)
                || !List.of("lecture", "practice", "lab").contains(type) || !type.equals(target.get("lesson_type"))) {
            throw new IllegalArgumentException("v2 lesson transfer has inconsistent one-off origin or authority");
        }
        return origin;
    }

    private static String hashOneOffBatch(Map<?, ?> payload, Map<?, ?> source, Map<?, ?> target,
                                         long origin, String bindingsHash) {
        StringBuilder canonical = new StringBuilder("ONE_OFF_TRANSFER_V2");
        append(canonical, Long.toString(origin));
        append(canonical, uuid(payload.get("operation_id"), "operation_id").toString());
        append(canonical, uuid(payload.get("request_key"), "request_key").toString());
        append(canonical, hash(payload.get("transfer_payload_hash"), "transfer_payload_hash"));
        for (String key : List.of("actor_id", "group_id", "semester_id", "occurrence_id", "transfer_revision",
                "source_lesson_id", "target_lesson_id")) {
            append(canonical, Long.toString(positiveLong(payload.get(key), key)));
        }
        append(canonical, Integer.toString(exactInt(payload.get("batch_index"), "batch_index")));
        append(canonical, Integer.toString(exactInt(payload.get("batch_count"), "batch_count")));
        appendSnapshot(canonical, source, "source");
        appendSnapshot(canonical, target, "target");
        append(canonical, bindingsHash);
        return hashText(canonical.toString());
    }

    private static void appendSnapshot(StringBuilder canonical, Map<?, ?> snapshot, String field) {
        for (String key : List.of("lesson_id", "one_off_lesson_id", "occurrence_id", "assignment_id", "group_id",
                "subject_id", "semester_id", "assigned_teacher_id", "generation", "lesson_revision", "occurrence_revision")) {
            append(canonical, Long.toString(positiveLong(snapshot.get(key), field + "." + key)));
        }
        LocalDate snapshotDate = date(snapshot.get("date"), field + ".date");
        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse((String) snapshot.get("start_time"));
            end = LocalTime.parse((String) snapshot.get("end_time"));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("v2 lesson transfer has invalid snapshot time", invalid);
        }
        Object room = snapshot.get("room");
        if (!end.isAfter(start) || room != null && !(room instanceof String)
                || exactInt(snapshot.get("day_of_week"), field + ".day_of_week") != snapshotDate.getDayOfWeek().getValue()
                || !"all".equals(snapshot.get("week_type_snapshot"))) {
            throw new IllegalArgumentException("v2 lesson transfer has invalid immutable physical slot");
        }
        append(canonical, (String) snapshot.get("lesson_type"));
        append(canonical, snapshotDate.toString());
        append(canonical, Integer.toString(lessonNumber(snapshot.get("lesson_number"), field + ".lesson_number")));
        append(canonical, start.toString());
        append(canonical, end.toString());
        append(canonical, room == null ? null : (String) room);
    }

    private static void append(StringBuilder canonical, String value) {
        canonical.append('|').append(value == null ? -1 : value.length()).append(':');
        if (value != null) canonical.append(value);
    }

    private static Map<?, ?> object(Object value, String field) {
        if (value instanceof Map<?, ?> object) return object;
        throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
    }

    private static UUID uuid(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field, invalid);
        }
    }

    private static String hash(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        try {
            byte[] decoded = HexFormat.of().parseHex(text);
            if (decoded.length != 32) throw new IllegalArgumentException();
            return HexFormat.of().formatHex(decoded);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field, invalid);
        }
    }

    private static LocalDate date(Object value, String field) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field, invalid);
        }
    }

    private static int lessonNumber(Object value, String field) {
        int number = exactInt(value, field);
        if (number < 1 || number > 8) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        return number;
    }

    private static int exactInt(Object value, String field) {
        long number = exactLong(value, field);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        return (int) number;
    }

    private static long positiveLong(Object value, String field) {
        long number = exactLong(value, field);
        if (number <= 0) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        return number;
    }

    private static long exactLong(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field);
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("lesson.transfer.requested has invalid " + field, invalid);
        }
    }
}
