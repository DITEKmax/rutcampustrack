package ru.rutcampustrack.shared.events;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Preserves origin IDs without replacing the active worker's framework-managed trace/span IDs. */
public abstract class AbstractEventConsumer {
    public static final String MDC_TRACE_ID = AbstractEventPublisher.MDC_TRACE_ID;
    public static final String MDC_ORIGIN_TRACE_ID = "originTraceId";
    private static final Pattern LEGACY_TRACE = Pattern.compile("[0-9a-f]{16}|[0-9a-f]{32}");

    protected <T extends DomainEvent> void withTraceContext(T event, Consumer<T> handler) {
        runInEventContext(
                event.getTraceId(),
                event.getRequestId(),
                event.getEventId() == null ? null : event.getEventId().toString(),
                () -> handler.accept(event));
    }

    protected void withTraceContext(Map<String, Object> envelope, Runnable handler) {
        runInEventContext(
                envelope == null ? null : envelope.get("trace_id"),
                envelope == null ? null : envelope.get("request_id"),
                envelope == null ? null : envelope.get("event_id"),
                handler);
    }

    private void runInEventContext(
            Object originTrace, Object requestId, Object eventId, Runnable handler) {
        String previousOrigin = MDC.get(MDC_ORIGIN_TRACE_ID);
        String previousRequest = MDC.get("requestId");
        String previousEvent = MDC.get("eventId");
        String origin =
                originTrace instanceof String value
                                && (LEGACY_TRACE.matcher(value).matches()
                                        || EventTraceContext.validRequestId(value) != null)
                        ? value
                        : null;
        replace(MDC_ORIGIN_TRACE_ID, origin);
        replace("requestId", EventTraceContext.validRequestId(requestId));
        replace("eventId", EventTraceContext.validRequestId(eventId));
        try {
            handler.run();
        } finally {
            replace("eventId", previousEvent);
            replace("requestId", previousRequest);
            replace(MDC_ORIGIN_TRACE_ID, previousOrigin);
        }
    }

    private static void replace(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
