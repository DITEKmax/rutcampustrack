package ru.rutcampustrack.shared.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

class AbstractEventConsumerTest {
    static class TestConsumer extends AbstractEventConsumer {
        void handle(Map<String, Object> envelope, Runnable handler) {
            withTraceContext(envelope, handler);
        }
    }

    private final TestConsumer consumer = new TestConsumer();
    private static final String WORKER_TRACE = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ORIGIN_TRACE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String REQUEST_ID = "11111111-1111-4111-8111-111111111111";
    private static final String EVENT_ID = "22222222-2222-4222-8222-222222222222";

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void preservesWorkerSpanWhileExposingBoundedOriginAndMessageCorrelation() {
        MDC.put("traceId", WORKER_TRACE);
        MDC.put("spanId", "cccccccccccccccc");
        consumer.handle(
                Map.of("trace_id", ORIGIN_TRACE, "request_id", REQUEST_ID, "event_id", EVENT_ID),
                () -> {
                    assertThat(MDC.get("traceId")).isEqualTo(WORKER_TRACE);
                    assertThat(MDC.get("spanId")).isEqualTo("cccccccccccccccc");
                    assertThat(MDC.get("originTraceId")).isEqualTo(ORIGIN_TRACE);
                    assertThat(MDC.get("requestId")).isEqualTo(REQUEST_ID);
                    assertThat(MDC.get("eventId")).isEqualTo(EVENT_ID);
                });
        assertThat(MDC.get("traceId")).isEqualTo(WORKER_TRACE);
        assertThat(MDC.get("spanId")).isEqualTo("cccccccccccccccc");
        assertThat(MDC.get("originTraceId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    @Test
    void failedDeliveryRestoresCallerAndCannotContaminateNextMessage() {
        MDC.put("originTraceId", WORKER_TRACE);
        assertThatThrownBy(
                        () ->
                                consumer.handle(
                                        Map.of("trace_id", ORIGIN_TRACE, "request_id", REQUEST_ID),
                                        () -> {
                                            throw new IllegalStateException("retry");
                                        }))
                .hasMessage("retry");
        assertThat(MDC.get("originTraceId")).isEqualTo(WORKER_TRACE);
        assertThat(MDC.get("requestId")).isNull();
        consumer.handle(
                Map.of(),
                () -> {
                    assertThat(MDC.get("originTraceId")).isNull();
                    assertThat(MDC.get("requestId")).isNull();
                });
        assertThat(MDC.get("originTraceId")).isEqualTo(WORKER_TRACE);
    }

    @Test
    void malformedOptionalDiagnosticValuesAreIgnoredWithoutFailingBusinessHandler() {
        consumer.handle(
                Map.of(
                        "trace_id",
                        Map.of("secret", "synthetic-marker"),
                        "request_id",
                        "synthetic-marker",
                        "event_id",
                        "synthetic-marker"),
                () -> {
                    assertThat(MDC.get("originTraceId")).isNull();
                    assertThat(MDC.get("requestId")).isNull();
                    assertThat(MDC.get("eventId")).isNull();
                });
    }
}
