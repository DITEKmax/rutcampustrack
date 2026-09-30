package ru.rutcampustrack.schedule.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.subject.SubjectDeletedCascadeService;
import ru.rutcampustrack.schedule.lesson.LessonTransferWriter;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.util.Map;
import java.math.BigDecimal;
import java.util.List;

/**
 * Generic RabbitMQ event consumer for Schedule Service.
 *
 * <p>Listens to the shared fanout {@code rut-uit.events} via a dedicated
 * durable queue {@code schedule-service.events}. Reads {@code event_type} from
 * the envelope and routes to handlers. Unknown event types are ignored
 * silently — the queue is fanout-subscribed, so it receives every published
 * domain event across the platform.
 *
 * <p>No try/catch around handlers — exceptions propagate so Spring AMQP
 * nacks the message to the DLQ (see {@link RabbitConfig}).
 */
@Component
public class EventConsumer extends AbstractEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    public static final String CONSUMER_ID = "schedule";

    private final SubjectDeletedCascadeService subjectDeletedCascadeService;
    private final IdempotencyGuard idempotencyGuard;
    private final LessonTransferWriter lessonTransferWriter;
    private final SemesterArchiveEffectLedger semesterArchiveEffectLedger;

    @Autowired
    public EventConsumer(SubjectDeletedCascadeService subjectDeletedCascadeService,
                         IdempotencyGuard idempotencyGuard,
                         LessonTransferWriter lessonTransferWriter,
                         SemesterArchiveEffectLedger semesterArchiveEffectLedger) {
        this.subjectDeletedCascadeService = subjectDeletedCascadeService;
        this.idempotencyGuard = idempotencyGuard;
        this.lessonTransferWriter = lessonTransferWriter;
        this.semesterArchiveEffectLedger = semesterArchiveEffectLedger;
    }

    @RabbitListener(queues = "schedule-service.events")
    @EventIdempotent(consumer = CONSUMER_ID)
    @Transactional
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
            return;
        }
        // M04 QA3 — extract trace_id из envelope в MDC до вызова handler'а.
        // Логи внутри handler'а получат тот же trace_id, что и producer-side.
        withTraceContext(envelope, () -> {
            log.debug("Received event: {}", eventType);
            switch (eventType) {
                case "subject.deleted" -> handleSubjectDeleted(envelope);
                case "lesson.transfer.participant.applied" -> handleLessonTransferAcknowledged(envelope);
                case "semester.archive.effect.ack" -> handleSemesterArchiveEffectAcknowledgement(envelope);
                default -> log.trace("Ignoring unknown event type: {}", eventType);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void handleSubjectDeleted(Map<String, Object> envelope) {
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?>)) {
            log.warn("subject.deleted: missing payload, ignoring: {}", envelope);
            return;
        }
        Map<String, Object> payload = (Map<String, Object>) rawPayload;
        Object rawId = payload.get("subject_id");
        if (!(rawId instanceof Number id)) {
            log.warn("subject.deleted: missing or non-numeric subject_id, ignoring: {}", payload);
            return;
        }
        subjectDeletedCascadeService.cascade(id.longValue());
    }

    @SuppressWarnings("unchecked")
    private void handleLessonTransferAcknowledged(Map<String, Object> envelope) {
        if (!List.of("academic-service", "attendance-service").contains(envelope.get("source"))) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has an untrusted source");
        }
        if (exactLong(envelope.get("event_version"), "event_version") != 1) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has an unsupported version");
        }
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has no payload object");
        }
        Map<String, Object> payload = (Map<String, Object>) raw;
        String participant = requiredString(payload.get("participant"), "participant");
        String expectedSource = "ACADEMIC".equals(participant) ? "academic-service"
                : "ATTENDANCE".equals(participant) ? "attendance-service" : null;
        if (expectedSource == null || !expectedSource.equals(envelope.get("source"))) {
            throw new IllegalArgumentException("lesson transfer acknowledgement participant does not match source");
        }
        String result = requiredString(payload.get("result"), "result");
        Object rawErrorCode = payload.get("error_code");
        String errorCode = rawErrorCode == null ? null : requiredString(rawErrorCode, "error_code");
        Object rawRetryable = payload.get("retryable");
        if (!(rawRetryable instanceof Boolean retryable)) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has invalid retryable flag");
        }
        lessonTransferWriter.acknowledge(
                requiredString(payload.get("operation_id"), "operation_id"),
                participant,
                Math.toIntExact(exactLong(payload.get("batch_index"), "batch_index")),
                result,
                errorCode,
                requiredString(payload.get("transfer_payload_hash"), "transfer_payload_hash"),
                exactLong(payload.get("source_lesson_id"), "source_lesson_id"),
                exactLong(payload.get("target_lesson_id"), "target_lesson_id"),
                retryable);
    }

    @SuppressWarnings("unchecked")
    private void handleSemesterArchiveEffectAcknowledgement(Map<String, Object> envelope) {
        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("semester archive effect acknowledgement has no payload");
        }
        Map<String, Object> payload = (Map<String, Object>) raw;
        String target = requiredString(payload.get("target"), "target");
        String source = requiredString(envelope.get("source"), "source");
        String eventType = requiredString(payload.get("source_event_type"), "source_event_type");
        String expectedSource = "ATTENDANCE".equals(target) ? "attendance-service"
                : "ACADEMIC".equals(target) ? "academic-service" : null;
        boolean expectedEffect = "ATTENDANCE".equals(target)
                ? List.of("lesson.closed", "lesson.cancelled", "lesson.deleted", "lesson.one_off.cancelled")
                    .contains(eventType)
                : "ACADEMIC".equals(target) && "homework.binding.archived".equals(eventType);
        if (expectedSource == null || !expectedSource.equals(source) || !expectedEffect) {
            throw new IllegalArgumentException("semester archive effect target does not match trusted source/event type");
        }
        if (exactLong(envelope.get("event_version"), "event_version") != 1) {
            throw new IllegalArgumentException("semester archive effect acknowledgement has unsupported version");
        }
        UUID sourceEventId = uuid(payload.get("source_event_id"), "source_event_id");
        UUID acknowledgementEventId = uuid(envelope.get("event_id"), "event_id");
        long semesterId = exactLong(payload.get("semester_id"), "semester_id");
        if (semesterId <= 0) {
            throw new IllegalArgumentException("semester archive effect acknowledgement has invalid semester_id");
        }
        String result = requiredString(payload.get("result"), "result");
        Object rawReason = payload.get("blocking_reason");
        String reason = rawReason == null ? null : requiredString(rawReason, "blocking_reason");
        semesterArchiveEffectLedger.acknowledge(
                sourceEventId,
                target,
                eventType,
                semesterId,
                requiredString(payload.get("payload_hash"), "payload_hash"),
                result,
                reason,
                acknowledgementEventId);
    }

    private static String requiredString(Object raw, String field) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has invalid " + field);
        }
        return value;
    }

    private static long exactLong(Object raw, String field) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has invalid " + field);
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException invalid) {
            throw new IllegalArgumentException("lesson transfer acknowledgement has invalid " + field, invalid);
        }
    }

    private static UUID uuid(Object raw, String field) {
        if (!(raw instanceof String value)) {
            throw new IllegalArgumentException("semester archive effect acknowledgement has invalid " + field);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException(
                    "semester archive effect acknowledgement has invalid " + field, malformed);
        }
    }
}
