package ru.rutcampustrack.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;

/**
 * Fluent helper над {@link MeterRegistry} для бизнес-метрик из {@link MetricNames}. Используется
 * вместо {@code @Counted} там, где нужен control над тегами в момент вызова.
 *
 * <p>Сервис создаёт singleton-bean {@code new BusinessMetrics(meterRegistry)}.
 *
 * <pre>{@code
 * @Bean
 * BusinessMetrics businessMetrics(MeterRegistry registry) {
 *     return new BusinessMetrics(registry);
 * }
 *
 * // ...
 * businessMetrics.incrementCheckin("present");
 * }</pre>
 */
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public Counter loginCounter(String role) {
        return Counter.builder(MetricNames.AUTH_LOGIN)
                .tags(Tags.of("role", nullToUnknown(role)))
                .register(registry);
    }

    public Counter logoutCounter(String cause) {
        return Counter.builder(MetricNames.AUTH_LOGOUT)
                .tags(Tags.of("cause", nullToUnknown(cause)))
                .register(registry);
    }

    public Counter otpRequestCounter(String channel) {
        return Counter.builder(MetricNames.OTP_REQUEST)
                .tags(Tags.of("channel", nullToUnknown(channel)))
                .register(registry);
    }

    public Counter otpVerifyCounter(String outcome) {
        return Counter.builder(MetricNames.OTP_VERIFY)
                .tags(Tags.of("outcome", nullToUnknown(outcome)))
                .register(registry);
    }

    public Counter checkinCounter(String status) {
        return Counter.builder(MetricNames.ATTENDANCE_CHECKIN)
                .tags(Tags.of("status", nullToUnknown(status)))
                .register(registry);
    }

    public Counter excuseCreatedCounter(String kind) {
        return Counter.builder(MetricNames.EXCUSE_CREATED)
                .tags(Tags.of("kind", nullToUnknown(kind)))
                .register(registry);
    }

    public Counter lateCheckinCreatedCounter() {
        return Counter.builder(MetricNames.LATE_CHECKIN_CREATED).register(registry);
    }

    /** Records committed business outcomes; a rolled-back transaction contributes no success. */
    public void incrementAfterCommit(Counter counter) {
        Objects.requireNonNull(counter, "counter");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            counter.increment();
                        }
                    });
        } else {
            counter.increment();
        }
    }

    /**
     * KI-2 — счётчик пробивает dual-mode fallback (internal-JWT отсутствует или невалиден, фильтр
     * падает на legacy headers X-User-Id/X-User-Role).
     *
     * @param from источник, на который downgrade (например {@code "internal-jwt"}).
     * @param to куда упал (например {@code "legacy-headers"}).
     */
    public Counter internalJwtFallbackCounter(String from, String to) {
        return Counter.builder(MetricNames.INTERNAL_JWT_FALLBACK)
                .tags(Tags.of("from", nullToUnknown(from), "to", nullToUnknown(to)))
                .register(registry);
    }

    private static String nullToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
