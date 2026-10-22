package ru.rutcampustrack.shared.observability;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Exports useful protocol outcomes and URI templates without raw request or exception content. */
public final class SafeTraceSpanFilter implements SpanFilter {
    private static final Set<String> METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "CONNECT", "TRACE");
    private static final Set<String> PROTOCOL_KEYS =
            Set.of(
                    "rpc.system",
                    "rpc.service",
                    "rpc.method",
                    "rpc.type",
                    "rpc.error_code",
                    "rpc.grpc.status_code",
                    "grpc.status_code",
                    "grpc.status",
                    "status",
                    "exception",
                    "error.type",
                    "messaging.system",
                    "messaging.operation",
                    "messaging.operation.name",
                    "messaging.operation.type");
    private static final Pattern PROTOCOL_VALUE = Pattern.compile("[A-Za-z0-9_.$-]{1,160}");

    @Override
    public FinishedSpan map(FinishedSpan span) {
        SpanData original = OtelFinishedSpan.toOtel(span);
        Attributes safeAttributes = safeAttributes(original.getAttributes(), original.getKind());
        List<EventData> safeEvents =
                original.getEvents().stream()
                        .map(
                                event ->
                                        EventData.create(
                                                event.getEpochNanos(),
                                                "exception".equals(event.getName())
                                                        ? "exception"
                                                        : "protocol.event",
                                                safeExceptionType(event.getAttributes())))
                        .toList();
        List<LinkData> safeLinks =
                original.getLinks().stream()
                        .map(link -> LinkData.create(withoutVendorState(link.getSpanContext())))
                        .toList();
        return OtelFinishedSpan.fromOtel(
                new DelegatingSpanData(original) {
                    @Override
                    public String getName() {
                        String method = safeAttributes.get(AttributeKey.stringKey("http.method"));
                        if (method == null) {
                            method =
                                    safeAttributes.get(
                                            AttributeKey.stringKey("http.request.method"));
                        }
                        if (method != null) {
                            return "http " + method;
                        }
                        String rpcService =
                                safeAttributes.get(AttributeKey.stringKey("rpc.service"));
                        String rpcMethod = safeAttributes.get(AttributeKey.stringKey("rpc.method"));
                        return rpcService != null && rpcMethod != null
                                ? rpcService + "/" + rpcMethod
                                : "operation";
                    }

                    @Override
                    public Attributes getAttributes() {
                        return safeAttributes;
                    }

                    @Override
                    public List<EventData> getEvents() {
                        return safeEvents;
                    }

                    @Override
                    public List<LinkData> getLinks() {
                        return safeLinks;
                    }

                    @Override
                    public StatusData getStatus() {
                        return StatusData.create(original.getStatus().getStatusCode(), "");
                    }

                    @Override
                    public SpanContext getSpanContext() {
                        return withoutVendorState(original.getSpanContext());
                    }

                    @Override
                    public SpanContext getParentSpanContext() {
                        return withoutVendorState(original.getParentSpanContext());
                    }

                    @Override
                    public int getTotalAttributeCount() {
                        return safeAttributes.size();
                    }
                });
    }

    private static Attributes safeAttributes(Attributes attributes, SpanKind kind) {
        var safe = Attributes.builder();
        attributes.forEach(
                (key, rawValue) -> {
                    String name = key.getKey();
                    String value = String.valueOf(rawValue);
                    boolean allowed =
                            switch (name) {
                                case "http.method", "http.request.method" ->
                                        METHODS.contains(value);
                                case "http.status_code", "http.response.status_code" ->
                                        value.matches("[1-5][0-9]{2}");
                                case "requestId", "messaging.message.id" ->
                                        RequestCorrelation.isCanonical(value);
                                // Only server conventions guarantee registered route templates.
                                // Client uri may be a literal capability path passed to WebClient.
                                case "uri", "http.route" ->
                                        kind == SpanKind.SERVER
                                                && value.length() <= 256
                                                && value.startsWith("/")
                                                && !value.contains("?")
                                                && !value.contains("#")
                                                && !value.contains("://");
                                default ->
                                        PROTOCOL_KEYS.contains(name)
                                                && PROTOCOL_VALUE.matcher(value).matches();
                            };
                    if (allowed) {
                        put(safe, key, rawValue);
                    }
                });
        return safe.build();
    }

    private static Attributes safeExceptionType(Attributes attributes) {
        String type = attributes.get(AttributeKey.stringKey("exception.type"));
        return type != null && PROTOCOL_VALUE.matcher(type).matches()
                ? Attributes.of(AttributeKey.stringKey("exception.type"), type)
                : Attributes.empty();
    }

    private static SpanContext withoutVendorState(SpanContext context) {
        return context.isRemote()
                ? SpanContext.createFromRemoteParent(
                        context.getTraceId(),
                        context.getSpanId(),
                        context.getTraceFlags(),
                        TraceState.getDefault())
                : SpanContext.create(
                        context.getTraceId(),
                        context.getSpanId(),
                        context.getTraceFlags(),
                        TraceState.getDefault());
    }

    private static <T> void put(
            io.opentelemetry.api.common.AttributesBuilder target,
            AttributeKey<T> key,
            Object value) {
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        target.put(key, typed);
    }
}
