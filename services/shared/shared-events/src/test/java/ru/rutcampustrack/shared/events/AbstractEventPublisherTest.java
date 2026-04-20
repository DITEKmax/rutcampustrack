package ru.rutcampustrack.shared.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

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
        WithVersion() {
            super("test.with-version", null);
        }
    }

    static class WithoutVersion extends DomainEvent {
        WithoutVersion() {
            super("test.without-version", null);
        }
    }

    @EventVersion(5)
    static abstract class BaseVersioned extends DomainEvent {
        BaseVersioned(String type) {
            super(type, null);
        }
    }

    static class SubclassInheritsVersion extends BaseVersioned {
        SubclassInheritsVersion() {
            super("test.subclass-inherits-version");
        }
    }

    private final TestPublisher publisher = new TestPublisher();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("fillDefaults заполняет все стандартные поля когда они null")
    void fillAllWhenNull() {
        MDC.put(AbstractEventPublisher.MDC_TRACE_ID, "mdc-trace-abc");

        WithVersion event = publisher.publish(new WithVersion());

        assertThat(event.getEventType()).isEqualTo("test.with-version");
        assertThat(event.getEventId()).isNotNull();
        assertThat(event.getEventVersion()).isEqualTo(3);
        assertThat(event.getTraceId()).isEqualTo("mdc-trace-abc");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.getSourceService()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("Пре-заполненные поля не перезаписываются")
    void doNotOverrideExplicit() {
        MDC.put(AbstractEventPublisher.MDC_TRACE_ID, "mdc-trace");

        WithVersion event = new WithVersion();
        event.setEventVersion(99);
        event.setTraceId("explicit-trace");
        OffsetDateTime explicitTime = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        event.setOccurredAt(explicitTime);
        event.setSourceService("other-service");

        publisher.publish(event);

        assertThat(event.getEventVersion()).isEqualTo(99);
        assertThat(event.getTraceId()).isEqualTo("explicit-trace");
        assertThat(event.getOccurredAt()).isEqualTo(explicitTime);
        assertThat(event.getSourceService()).isEqualTo("other-service");
    }

    @Test
    @DisplayName("Без MDC traceId — fallback на UUID (M04 QA3 required в schema)")
    void withoutMdcTraceIdFallbackToUuid() {
        WithVersion event = publisher.publish(new WithVersion());
        assertThat(event.getTraceId())
                .as("traceId должен иметь fallback UUID когда MDC пуст")
                .isNotNull()
                .matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
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
