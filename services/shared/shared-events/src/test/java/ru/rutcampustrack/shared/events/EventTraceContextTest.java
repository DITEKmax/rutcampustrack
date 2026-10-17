package ru.rutcampustrack.shared.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

class EventTraceContextTest {
    private static final String TRACE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PARENT = "bbbbbbbbbbbbbbbb";
    private static final String REQUEST = "11111111-1111-4111-8111-111111111111";

    static class Event extends DomainEvent {
        Event() {
            super("lesson.started", Map.of("lesson_id", 42));
        }
    }

    static class Publisher extends AbstractEventPublisher {
        Publisher() {
            super("schedule-service");
        }

        Event prepare(Event event) {
            return fillDefaults(event);
        }
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void parentAndRequestSurviveDurableJsonAndRestoreAcrossRetryFailure() throws Exception {
        var source =
                Span.wrap(
                        SpanContext.create(
                                TRACE, PARENT, TraceFlags.getSampled(), TraceState.getDefault()));
        Event event;
        try (var ignored = Context.root().with(source).makeCurrent()) {
            MDC.put("requestId", REQUEST);
            event = new Publisher().prepare(new Event());
            Map<String, Object> mapEnvelope = new HashMap<>();
            EventTraceContext.capture(mapEnvelope);
            assertThat(mapEnvelope)
                    .containsEntry("traceparent", event.getTraceparent())
                    .containsEntry("request_id", REQUEST)
                    .doesNotContainKey("tracestate");
        }
        MDC.clear();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Map<String, Object> envelope =
                mapper.readValue(mapper.writeValueAsBytes(event), new TypeReference<>() {});
        assertThat(envelope)
                .containsEntry("traceparent", "00-" + TRACE + "-" + PARENT + "-01")
                .containsEntry("request_id", REQUEST)
                .doesNotContainKey("tracestate");
        assertThatThrownBy(
                        () -> {
                            try (var ignored = EventTraceContext.openParent(envelope)) {
                                assertThat(Span.current().getSpanContext().getTraceId())
                                        .isEqualTo(TRACE);
                                assertThat(Span.current().getSpanContext().getSpanId())
                                        .isEqualTo(PARENT);
                                assertThat(Span.current().getSpanContext().isRemote()).isTrue();
                                assertThat(Baggage.current().getEntryValue("requestId"))
                                        .isEqualTo(REQUEST);
                                throw new IllegalStateException("retry");
                            }
                        })
                .hasMessage("retry");
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
        assertThat(Baggage.current().getEntryValue("requestId")).isNull();
        try (var ignored = EventTraceContext.openParent(Map.of("trace_id", REQUEST))) {
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            assertThat(Baggage.current().getEntryValue("requestId")).isNull();
        }
    }

    @Test
    void rejectsInvalidOrOversizedMetadataAndNeverUsesLegacyUuidAsTraceParent() {
        for (Object invalid :
                new Object[] {
                    "synthetic-secret-marker",
                    REQUEST,
                    "00-" + "0".repeat(32) + "-" + PARENT + "-01",
                    "a".repeat(8192),
                    42
                }) {
            assertThat(EventTraceContext.validTraceparent(invalid)).isNull();
        }
        try (var ignored =
                EventTraceContext.openParent(
                        Map.of(
                                "traceparent",
                                "synthetic-marker",
                                "request_id",
                                "synthetic-marker",
                                "trace_id",
                                REQUEST))) {
            assertThat(Span.current().getSpanContext().isValid()).isFalse();
            assertThat(Baggage.current().isEmpty()).isTrue();
        }
    }
}
