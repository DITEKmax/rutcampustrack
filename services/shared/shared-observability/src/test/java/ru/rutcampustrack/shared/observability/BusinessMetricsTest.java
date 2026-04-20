package ru.rutcampustrack.shared.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private MeterRegistry registry;
    private BusinessMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new BusinessMetrics(registry);
    }

    @Test
    void loginCounter_tagsByRole() {
        metrics.loginCounter("student").increment();
        metrics.loginCounter("teacher").increment();
        metrics.loginCounter("student").increment();

        MetricsTestSupport.assertCounter(registry, MetricNames.AUTH_LOGIN, "role", "student", 2);
        MetricsTestSupport.assertCounter(registry, MetricNames.AUTH_LOGIN, "role", "teacher", 1);
    }

    @Test
    void nullRole_normalizedToUnknown() {
        metrics.loginCounter(null).increment();
        metrics.loginCounter("").increment();
        metrics.loginCounter("  ").increment();

        MetricsTestSupport.assertCounter(registry, MetricNames.AUTH_LOGIN, "role", "unknown", 3);
    }

    @Test
    void internalJwtFallbackCounter_twoTags() {
        metrics.internalJwtFallbackCounter("internal-jwt", "legacy-headers").increment();

        MetricsTestSupport.assertCounter(registry, MetricNames.INTERNAL_JWT_FALLBACK, "from", "internal-jwt", 1);
        MetricsTestSupport.assertCounter(registry, MetricNames.INTERNAL_JWT_FALLBACK, "to", "legacy-headers", 1);
    }

    @Test
    void checkinCounter_perStatus() {
        metrics.checkinCounter("present").increment();
        metrics.checkinCounter("present").increment();
        metrics.checkinCounter("late").increment();

        MetricsTestSupport.assertCounter(registry, MetricNames.ATTENDANCE_CHECKIN, "status", "present", 2);
        MetricsTestSupport.assertCounter(registry, MetricNames.ATTENDANCE_CHECKIN, "status", "late", 1);
    }

    @Test
    void lateCheckinCounter_noTags() {
        metrics.lateCheckinCreatedCounter().increment();
        metrics.lateCheckinCreatedCounter().increment();

        MetricsTestSupport.assertCounterAny(registry, MetricNames.LATE_CHECKIN_CREATED, 2);
    }

    @Test
    void registry_isShared_acrossInvocations() {
        // Counter с одинаковыми тегами не пересоздаётся, а переиспользуется.
        metrics.loginCounter("student").increment();
        metrics.loginCounter("student").increment();

        assertThat(registry.find(MetricNames.AUTH_LOGIN).counters()).hasSize(1);
    }
}
