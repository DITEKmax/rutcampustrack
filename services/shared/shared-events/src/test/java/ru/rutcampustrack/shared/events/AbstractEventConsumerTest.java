package ru.rutcampustrack.shared.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractEventConsumerTest {

    static class TestConsumer extends AbstractEventConsumer {
        <T extends DomainEvent> void handle(T event, java.util.function.Consumer<T> handler) {
            withTraceContext(event, handler);
        }
    }

    static class AnyEvent extends DomainEvent {
    }

    private final TestConsumer consumer = new TestConsumer();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("trace_id события кладётся в MDC во время handler'а")
    void putsTraceIdIntoMdc() {
        AnyEvent event = new AnyEvent();
        event.setTraceId("trace-xyz");
        AtomicReference<String> seen = new AtomicReference<>();

        consumer.handle(event, e -> seen.set(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)));

        assertThat(seen.get()).isEqualTo("trace-xyz");
    }

    @Test
    @DisplayName("MDC восстанавливается до previous value после handler'а")
    void restoresPreviousMdcAfterHandler() {
        MDC.put(AbstractEventConsumer.MDC_TRACE_ID, "outer-trace");
        AnyEvent event = new AnyEvent();
        event.setTraceId("inner-trace");

        consumer.handle(event, e -> {
            assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("inner-trace");
        });

        assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("outer-trace");
    }

    @Test
    @DisplayName("MDC чистится если previous был null")
    void clearsMdcWhenNoPrevious() {
        AnyEvent event = new AnyEvent();
        event.setTraceId("trace-x");

        consumer.handle(event, e -> {
            assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("trace-x");
        });

        assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isNull();
    }

    @Test
    @DisplayName("MDC восстанавливается даже при исключении handler'а")
    void restoresMdcOnException() {
        MDC.put(AbstractEventConsumer.MDC_TRACE_ID, "outer");
        AnyEvent event = new AnyEvent();
        event.setTraceId("inner");

        assertThatThrownBy(() -> consumer.handle(event, e -> {
            throw new RuntimeException("handler boom");
        })).hasMessage("handler boom");

        assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("outer");
    }

    @Test
    @DisplayName("Событие без trace_id — MDC не трогается (не затирает существующий)")
    void nullTraceIdDoesNotClobberMdc() {
        MDC.put(AbstractEventConsumer.MDC_TRACE_ID, "existing");
        AnyEvent event = new AnyEvent();
        // traceId == null

        consumer.handle(event, e -> {
            assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("existing");
        });

        assertThat(MDC.get(AbstractEventConsumer.MDC_TRACE_ID)).isEqualTo("existing");
    }
}
