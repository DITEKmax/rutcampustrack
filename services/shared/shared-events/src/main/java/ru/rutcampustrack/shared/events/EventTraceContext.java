package ru.rutcampustrack.shared.events;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Bounded durable W3C parent metadata; transport observations still create every worker span. */
public final class EventTraceContext {
    private static final Pattern TRACEPARENT =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}");
    private static final Pattern REQUEST_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final TextMapGetter<Map<String, String>> GETTER =
            new TextMapGetter<>() {
                @Override
                public Iterable<String> keys(Map<String, String> carrier) {
                    return carrier.keySet();
                }

                @Override
                public String get(Map<String, String> carrier, String key) {
                    return carrier == null ? null : carrier.get(key);
                }
            };

    private EventTraceContext() {}

    /** Called before the existing outbox serializes the envelope inside the domain transaction. */
    static void capture(DomainEvent event) {
        if (event.getTraceparent() == null) {
            Map<String, String> carrier = new HashMap<>();
            W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
            event.setTraceparent(carrier.get("traceparent"));
        }
        if (event.getRequestId() == null) {
            event.setRequestId(currentRequestId());
        }
    }

    /** Same durable metadata for the existing Map-based attendance envelope. */
    public static void capture(Map<String, Object> envelope) {
        Map<String, String> carrier = new HashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), carrier, Map::put);
        String parent = validTraceparent(envelope.get("traceparent"));
        if (parent == null) {
            parent = validTraceparent(carrier.get("traceparent"));
        }
        if (parent != null) {
            envelope.put("traceparent", parent);
        } else {
            envelope.remove("traceparent");
        }
        String requestId = validRequestId(envelope.get("request_id"));
        if (requestId == null) {
            requestId = currentRequestId();
        }
        if (requestId != null) {
            envelope.put("request_id", requestId);
        } else {
            envelope.remove("request_id");
        }
    }

    private static String currentRequestId() {
        String requestId = validRequestId(Baggage.current().getEntryValue("requestId"));
        return requestId == null ? validRequestId(MDC.get("requestId")) : requestId;
    }

    /**
     * Restores only the persisted parent and canonical request ID while a standard RabbitTemplate
     * observation runs. Closing the scope restores the caller even after a transport failure.
     */
    public static Scope openParent(Map<String, ?> envelope) {
        String parent = validTraceparent(envelope == null ? null : envelope.get("traceparent"));
        Context context = Context.root();
        if (parent != null) {
            context =
                    W3CTraceContextPropagator.getInstance()
                            .extract(context, Map.of("traceparent", parent), GETTER);
        }
        String requestId = validRequestId(envelope == null ? null : envelope.get("request_id"));
        if (requestId != null) {
            context = Baggage.builder().put("requestId", requestId).build().storeInContext(context);
        }
        return context.makeCurrent();
    }

    public static String validRequestId(Object value) {
        return value instanceof String id && REQUEST_ID.matcher(id).matches() ? id : null;
    }

    public static String validTraceparent(Object value) {
        if (!(value instanceof String parent) || !TRACEPARENT.matcher(parent).matches()) {
            return null;
        }
        Context extracted =
                W3CTraceContextPropagator.getInstance()
                        .extract(Context.root(), Map.of("traceparent", parent), GETTER);
        return Span.fromContext(extracted).getSpanContext().isValid() ? parent : null;
    }
}
