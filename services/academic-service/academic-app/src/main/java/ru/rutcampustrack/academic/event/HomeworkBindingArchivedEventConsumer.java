package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.ApplicationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.homework.HomeworkBindingArchiveCoordinator;
import ru.rutcampustrack.academic.homework.HomeworkBindingTransferCoordinator;
import ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Consumes Schedule's terminal homework-binding archive event. */
@Component
@Slf4j
public class HomeworkBindingArchivedEventConsumer extends AbstractEventConsumer {

    public static final String CONSUMER_ID = "academic-homework-archive";
    private static final String EVENT_TYPE = "homework.binding.archived";
    private static final String EVENT_SOURCE = "schedule-service";
    private static final long SUPPORTED_EVENT_VERSION = 1L;

    private final HomeworkBindingArchiveCoordinator archiveCoordinator;
    private final HomeworkBindingTransferCoordinator transferCoordinator;
    private final AcademicSemesterArchiveBarrierTransaction archiveBarrier;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public HomeworkBindingArchivedEventConsumer(
            HomeworkBindingArchiveCoordinator archiveCoordinator,
            HomeworkBindingTransferCoordinator transferCoordinator,
            AcademicSemesterArchiveBarrierTransaction archiveBarrier,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher) {
        this.archiveCoordinator = archiveCoordinator;
        this.transferCoordinator = transferCoordinator;
        this.archiveBarrier = archiveBarrier;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @RabbitListener(queues = RabbitConfig.HOMEWORK_ARCHIVE_EVENTS_QUEUE)
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        Object rawEventType = envelope == null ? null : envelope.get("event_type");
        if (!(rawEventType instanceof String eventType) || eventType.isBlank()) {
            log.warn("Ignoring Academic event envelope without event_type");
            return;
        }
        if (!EVENT_TYPE.equals(eventType)) {
            log.debug("Ignoring Academic event type {}", eventType);
            return;
        }

        ArchiveCommand command = parseTargetEvent(envelope);
        byte[] payloadHash = semanticPayloadHash(envelope.get("payload"));
        EffectReceipt receipt = claimReceipt(command, payloadHash);
        if (receipt.applied()) {
            publishAcknowledgement(command, payloadHash, receipt.acknowledgementEventId());
            return;
        }

        boolean[] trackedNoContentCancellation = {false};
        withTraceContext(envelope, () -> {
            if (command.homeworkId() == null) {
                trackedNoContentCancellation[0] = archiveBarrier.trackPendingArchivedBinding(
                        command.semesterId(), command.bindingId(),
                        command.actorId(), command.requestKey(), command.occurrenceId(),
                        command.bindingRevision(), command.sourceEventId(), payloadHash);
            }
            archiveCoordinator.archiveCancelledBinding(
                    command.bindingId(), command.actorId(), command.requestKey(), command.homeworkId(),
                    command.semesterId(), command.sourceEventId());
            if (trackedNoContentCancellation[0]) {
                transferCoordinator.cancelPendingUnpublished(command.bindingId(), command.actorId(),
                        command.requestKey(), command.semesterId());
            }
        });

        int updated = jdbc.update("""
                UPDATE academic_semester_archive_effect_receipts
                   SET state = 'APPLIED', applied_at = now()
                 WHERE source_event_id = ? AND event_type = ? AND semester_id = ?
                   AND binding_id = ? AND payload_hash = ? AND state = 'PENDING'
                """, command.sourceEventId(), EVENT_TYPE, command.semesterId(),
                command.bindingId(), payloadHash);
        if (updated != 1) {
            throw new IllegalStateException("Academic archive effect receipt changed during application");
        }
        if (trackedNoContentCancellation[0]) {
            archiveBarrier.completeTrackedNoContentEffect(command.semesterId(), command.bindingId(),
                    command.actorId(), command.requestKey(), command.sourceEventId());
        }
        publishAcknowledgement(command, payloadHash, receipt.acknowledgementEventId());
    }

    private void publishAcknowledgement(ArchiveCommand command, byte[] payloadHash,
                                        UUID acknowledgementEventId) {
        eventPublisher.publishEvent(new SemesterArchiveEffectAcknowledgementEvent(
                command.sourceEventId(), command.semesterId(), payloadHash, acknowledgementEventId));
    }

    private static ArchiveCommand parseTargetEvent(Map<String, Object> envelope) {
        if (!EVENT_SOURCE.equals(envelope.get("source"))) {
            throw new IllegalArgumentException("homework.binding.archived has an untrusted source");
        }
        if (positiveLong(envelope.get("event_version"), "event_version")
                != SUPPORTED_EVENT_VERSION) {
            throw new IllegalArgumentException("homework.binding.archived has an unsupported version");
        }
        UUID sourceEventId = uuid(envelope.get("event_id"), "event_id");

        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("homework.binding.archived has no payload object");
        }
        long bindingId = positiveLong(payload.get("binding_id"), "binding_id");
        long actorId = positiveLong(payload.get("actor_id"), "actor_id");
        UUID requestKey = uuid(payload.get("request_key"), "request_key");
        long occurrenceId = positiveLong(payload.get("occurrence_id"), "occurrence_id");
        long lessonId = positiveLong(payload.get("lesson_id"), "lesson_id");
        Object rawHomeworkId = payload.get("homework_id");
        Long homeworkId = rawHomeworkId == null
                ? null : positiveLong(rawHomeworkId, "homework_id");
        long bindingRevision = positiveLong(payload.get("binding_revision"), "binding_revision");
        long semesterId = positiveLong(payload.get("semester_id"), "semester_id");
        if (bindingRevision < 2) {
            throw new IllegalArgumentException("homework.binding.archived has an invalid binding_revision");
        }
        return new ArchiveCommand(bindingId, actorId, requestKey,
                occurrenceId, lessonId, homeworkId, bindingRevision, semesterId, sourceEventId);
    }

    private static long positiveLong(Object raw, String field) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("homework.binding.archived has invalid " + field);
        }
        final long value;
        try {
            value = new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("homework.binding.archived has invalid " + field, invalid);
        }
        if (value <= 0) {
            throw new IllegalArgumentException("homework.binding.archived has invalid " + field);
        }
        return value;
    }

    private static UUID uuid(Object raw, String field) {
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("homework.binding.archived has invalid " + field);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("homework.binding.archived has invalid " + field, invalid);
        }
    }

    private record ArchiveCommand(long bindingId, long actorId, UUID requestKey,
                                  long occurrenceId, long lessonId, Long homeworkId,
                                  long bindingRevision, long semesterId, UUID sourceEventId) {
    }

    private EffectReceipt claimReceipt(ArchiveCommand command, byte[] payloadHash) {
        UUID acknowledgementEventId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO academic_semester_archive_effect_receipts
                    (source_event_id, event_type, semester_id, binding_id, payload_hash,
                     acknowledgement_event_id)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_event_id) DO NOTHING
                """, command.sourceEventId(), EVENT_TYPE, command.semesterId(),
                command.bindingId(), payloadHash, acknowledgementEventId);
        Map<String, Object> receipt = jdbc.queryForMap("""
                SELECT event_type, semester_id, binding_id, payload_hash, state,
                       acknowledgement_event_id
                  FROM academic_semester_archive_effect_receipts
                 WHERE source_event_id = ? FOR UPDATE
                """, command.sourceEventId());
        if (!EVENT_TYPE.equals(receipt.get("event_type"))
                || ((Number) receipt.get("semester_id")).longValue() != command.semesterId()
                || ((Number) receipt.get("binding_id")).longValue() != command.bindingId()
                || !Arrays.equals((byte[]) receipt.get("payload_hash"), payloadHash)) {
            throw new IllegalArgumentException("Schedule archive event id was reused with a different effect identity");
        }
        return new EffectReceipt("APPLIED".equals(receipt.get("state")),
                (UUID) receipt.get("acknowledgement_event_id"));
    }

    private byte[] semanticPayloadHash(Object rawPayload) {
        JsonNode payload = objectMapper.valueToTree(rawPayload);
        try {
            byte[] canonical = objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(sortObjectKeys(payload));
            return MessageDigest.getInstance("SHA-256").digest(canonical);
        } catch (Exception failure) {
            if (failure instanceof NoSuchAlgorithmException) {
                throw new IllegalStateException("SHA-256 is not available", failure);
            }
            throw new IllegalArgumentException("Could not canonicalize Schedule archive effect payload", failure);
        }
    }

    private JsonNode sortObjectKeys(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            ArrayList<String> keys = new ArrayList<>();
            Iterator<String> fields = value.fieldNames();
            while (fields.hasNext()) keys.add(fields.next());
            keys.sort(String::compareTo);
            for (String key : keys) sorted.set(key, sortObjectKeys(value.get(key)));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode sorted = objectMapper.createArrayNode();
            for (JsonNode item : value) sorted.add(sortObjectKeys(item));
            return sorted;
        }
        return value.deepCopy();
    }

    private record EffectReceipt(boolean applied, UUID acknowledgementEventId) { }
}
