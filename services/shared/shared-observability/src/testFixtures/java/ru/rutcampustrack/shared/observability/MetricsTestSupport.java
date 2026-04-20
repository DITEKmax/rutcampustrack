package ru.rutcampustrack.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AssertJ-friendly хелперы для тестов сервисов, которые проверяют что
 * {@link BusinessMetrics} счётчик инкрементирован.
 *
 * <pre>{@code
 * MetricsTestSupport.assertCounter(registry, MetricNames.AUTH_LOGIN, "role", "student", 1);
 * }</pre>
 */
public final class MetricsTestSupport {

    private MetricsTestSupport() {
    }

    /**
     * Проверяет что counter с указанными tag-key/value инкрементирован
     * ровно {@code expectedCount} раз.
     */
    public static void assertCounter(MeterRegistry registry,
                                     String name,
                                     String tagKey,
                                     String tagValue,
                                     double expectedCount) {
        Counter counter = registry.find(name).tags(Tags.of(tagKey, tagValue)).counter();
        assertThat(counter)
                .as("counter %s{%s=%s} not registered", name, tagKey, tagValue)
                .isNotNull();
        assertThat(counter.count())
                .as("counter %s{%s=%s} count", name, tagKey, tagValue)
                .isEqualTo(expectedCount);
    }

    public static void assertCounterAny(MeterRegistry registry, String name, double expectedCount) {
        Counter counter = registry.find(name).counter();
        assertThat(counter).as("counter %s not registered", name).isNotNull();
        assertThat(counter.count()).as("counter %s count", name).isEqualTo(expectedCount);
    }
}
