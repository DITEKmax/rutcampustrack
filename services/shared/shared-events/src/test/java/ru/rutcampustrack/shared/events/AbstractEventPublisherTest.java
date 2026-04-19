package ru.rutcampustrack.shared.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractEventPublisherTest {

    static class TestPublisher extends AbstractEventPublisher {
        TestPublisher() {
            super("test-service");
        }

        <T extends DomainEvent> T publish(T event) {
            return fillDefaults(event);
        }
    }

    @EventVersion(3)
    static class WithVersion extends DomainEvent {
    }

    static class WithoutVersion extends DomainEvent {
    }

    @EventVersion(5)
    static abstract class BaseVersioned extends DomainEvent {
    }

    static class SubclassInheritsVersion extends BaseVersioned {
    }

    private final TestPublisher publisher = new TestPublisher();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("fillDefaults заполняет все 4 стандартных поля когда они null")
    void fillAllWhenNull() {
        MDC.put(AbstractEventPublisher.MDC_TRACE_ID, "mdc-trace-abc");

        WithVersion event = publisher.publish(new WithVersion());

        assertThat(event.getEventVersion()).isEqualTo(3);
        assertThat(event.getTraceId()).isEqualTo("mdc-trace-abc");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getSource()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("Пре-заполненные поля не перезаписываются")
    void doNotOverrideExplicit() {
        MDC.put(AbstractEventPublisher.MDC_TRACE_ID, "mdc-trace");

        WithVersion event = new WithVersion();
        event.setEventVersion(99);
        event.setTraceId("explicit-trace");
        Instant explicitTime = Instant.parse("2020-01-01T00:00:00Z");
        event.setOccurredAt(explicitTime);
        event.setSource("other-service");

        publisher.publish(event);

        assertThat(event.getEventVersion()).isEqualTo(99);
        assertThat(event.getTraceId()).isEqualTo("explicit-trace");
        assertThat(event.getOccurredAt()).isEqualTo(explicitTime);
        assertThat(event.getSource()).isEqualTo("other-service");
    }

    @Test
    @DisplayName("Без MDC traceId остаётся null (пропускается, не падает)")
    void withoutMdcTraceIdNull() {
        WithVersion event = publisher.publish(new WithVersion());
        assertThat(event.getTraceId()).isNull();
    }

    @Test
    @DisplayName("@EventVersion отсутствует → версия 1 (дефолт)")
    void noAnnotationDefaultsToOne() {
        WithoutVersion event = publisher.publish(new WithoutVersion());
        assertThat(event.getEventVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("@EventVersion на superclass → наследуется подклассом")
    void versionInheritedFromSuperclass() {
        SubclassInheritsVersion event = publisher.publish(new SubclassInheritsVersion());
        assertThat(event.getEventVersion()).isEqualTo(5);
    }
}
