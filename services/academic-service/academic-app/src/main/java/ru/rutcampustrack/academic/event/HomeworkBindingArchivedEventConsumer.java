package ru.rutcampustrack.academic.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.homework.HomeworkBindingArchiveCoordinator;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;
import ru.rutcampustrack.shared.events.EventIdempotent;
import ru.rutcampustrack.shared.events.IdempotencyGuard;

import java.math.BigDecimal;
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
    private final IdempotencyGuard idempotencyGuard;

    public HomeworkBindingArchivedEventConsumer(
            HomeworkBindingArchiveCoordinator archiveCoordinator,
            IdempotencyGuard idempotencyGuard) {
        this.archiveCoordinator = archiveCoordinator;
        this.idempotencyGuard = idempotencyGuard;
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
        if (!idempotencyGuard.tryClaim(CONSUMER_ID, envelope)) {
            return;
        }
        withTraceContext(envelope, () -> archiveCoordinator.archiveCancelledBinding(
                command.bindingId(), command.actorId(), command.requestKey(), command.homeworkId()));
    }

    private static ArchiveCommand parseTargetEvent(Map<String, Object> envelope) {
        if (!EVENT_SOURCE.equals(envelope.get("source"))) {
            throw new IllegalArgumentException("homework.binding.archived has an untrusted source");
        }
        if (positiveLong(envelope.get("event_version"), "event_version")
                != SUPPORTED_EVENT_VERSION) {
            throw new IllegalArgumentException("homework.binding.archived has an unsupported version");
        }

        Object rawPayload = envelope.get("payload");
        if (!(rawPayload instanceof Map<?, ?> payload)) {
            throw new IllegalArgumentException("homework.binding.archived has no payload object");
        }
        if (!payload.containsKey("homework_id")) {
            throw new IllegalArgumentException("homework.binding.archived is missing homework_id");
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
        if (bindingRevision < 2) {
            throw new IllegalArgumentException("homework.binding.archived has an invalid binding_revision");
        }
        return new ArchiveCommand(bindingId, actorId, requestKey,
                occurrenceId, lessonId, homeworkId, bindingRevision);
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
                                  long bindingRevision) {
    }
}
