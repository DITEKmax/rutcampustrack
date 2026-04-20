package ru.rutcampustrack.attendance.event;

import org.slf4j.MDC;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Helper для построения envelope для attendance-publisher'ов (M04 QA3 / D5(a)).
 *
 * <p>Attendance исторически использует ручной {@code Map<String,Object>} envelope
 * вместо POJO-наследования shared.events.DomainEvent (из-за gRPC-Mongo
 * специфики и разных payload-форм). Этот helper добавляет required envelope
 * поля {@code event_version}, {@code trace_id}, {@code source} в едином
 * формате, совместимом с event-schemas/*.json.
 *
 * <p>{@code trace_id} читается из MDC (заполняется Spring Cloud Sleuth /
 * Micrometer Tracing — M04 QA2 Группа 5). Если MDC пуст, trace_id опускается.
 */
public final class EventEnvelope {

    private static final String SOURCE = "attendance-service";
    private static final int EVENT_VERSION = 1;

    private EventEnvelope() {
    }

    public static Map<String, Object> build(String eventType, Map<String, Object> payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("event_type", eventType);
        envelope.put("event_id", UUID.randomUUID().toString());
        envelope.put("event_version", EVENT_VERSION);
        envelope.put("occurred_at", Instant.now().toString());
        envelope.put("source", SOURCE);
        // M04 QA3 — trace_id required в schema. Fallback на UUID когда MDC пуст
        // (scheduled job, тестовый context). Real prod заполняет через Micrometer Tracing.
        String traceId = Optional.ofNullable(MDC.get(AbstractEventPublisher.MDC_TRACE_ID))
                .orElseGet(() -> UUID.randomUUID().toString());
        envelope.put("trace_id", traceId);
        envelope.put("payload", payload);
        return envelope;
    }
}
