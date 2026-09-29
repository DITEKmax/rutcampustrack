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
        if (positiveLong(envelope.get("event_version"), "event_version") != 1) {
            throw new IllegalArgumentException("lesson.transfer.requested has an unsupported version");
        }
        if (!(envelope.get("payload") instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("lesson.transfer.requested has no payload object");
        }

        UUID operationId = uuid(payload.get("operation_id"), "operation_id");
        UUID requestKey = uuid(payload.get("request_key"), "request_key");
        long actorId = positiveLong(payload.get("actor_id"), "actor_id");
        String operationHash = hash(payload.get("payload_hash"), "payload_hash");
        long occurrenceId = positiveLong(payload.get("occurrence_id"), "occurrence_id");
        long groupId = positiveLong(payload.get("group_id"), "group_id");
        long semesterId = positiveLong(payload.get("semester_id"), "semester_id");
        long expectedRevision = positiveLong(
                payload.get("expected_occurrence_revision"), "expected_occurrence_revision");
        long sourceLessonId = positiveLong(payload.get("source_lesson_id"), "source_lesson_id");
        long targetLessonId = positiveLong(payload.get("target_lesson_id"), "target_lesson_id");
        Map<?, ?> source = object(payload.get("source"), "source");
        Map<?, ?> target = object(payload.get("target"), "target");

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
                || positiveLong(source.get("subject_id"), "source.subject_id")
                    != positiveLong(target.get("subject_id"), "target.subject_id")
                || !"planned".equals(source.get("status"))
                || !"planned".equals(target.get("status"))) {
            throw new IllegalArgumentException("lesson.transfer.requested has inconsistent source/target snapshots");
        }
        long sourceGeneration = positiveLong(source.get("generation"), "source.generation");
        long targetGeneration = positiveLong(target.get("generation"), "target.generation");
        if (targetGeneration != sourceGeneration + 1 || positiveLong(target.get("revision"), "target.revision") != 1) {
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
        String batchHash = hashBindings(bindings);
        return new LessonTransferBatch(operationId, requestKey, actorId, operationHash,
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
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
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
