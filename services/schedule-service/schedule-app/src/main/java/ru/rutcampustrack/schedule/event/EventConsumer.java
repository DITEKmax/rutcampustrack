package ru.rutcampustrack.schedule.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.schedule.subject.SubjectDeletedCascadeService;
import ru.rutcampustrack.shared.events.AbstractEventConsumer;

import java.util.Map;

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

    private final SubjectDeletedCascadeService subjectDeletedCascadeService;

    public EventConsumer(SubjectDeletedCascadeService subjectDeletedCascadeService) {
        this.subjectDeletedCascadeService = subjectDeletedCascadeService;
    }

    @RabbitListener(queues = "schedule-service.events")
    public void onEvent(Map<String, Object> envelope) {
        String eventType = (String) envelope.get("event_type");
        if (eventType == null) {
            log.warn("Received event without event_type, ignoring: {}", envelope);
            return;
        }
        // M04 QA3 — extract trace_id из envelope в MDC до вызова handler'а.
        // Логи внутри handler'а получат тот же trace_id, что и producer-side.
        withTraceContext(envelope, () -> {
            log.debug("Received event: {}", eventType);
            switch (eventType) {
                case "subject.deleted" -> handleSubjectDeleted(envelope);
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
}
