package ru.rutcampustrack.shared.observability;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * M13 G10 — auto-config shared-observability tracing-составляющей.
 *
 * <p>Регистрирует {@link ActuatorTracingExcludeFilter} как Spring bean
 * типа {@link SpanExportingPredicate}. Spring Boot 3.x tracing auto-config
 * ({@code OpenTelemetryTracingAutoConfiguration#otelSpanProcessor}) собирает
 * все predicate'ы через {@code ObjectProvider<SpanExportingPredicate>} и
 * передаёт в {@code CompositeSpanExporter} — фильтр срабатывает между
 * {@code BatchSpanProcessor} и {@code OtlpExporter}, до отправки в Tempo.
 *
 * <p>Активируется только если {@code SpanExportingPredicate} есть в classpath
 * (т.е. consumer подключил {@code micrometer-tracing} либо через
 * {@code micrometer-tracing-bridge-otel}). Modules без tracing stack просто
 * игнорируют auto-config через {@code @ConditionalOnClass}.
 *
 * <p>Sampler-подход (изначальный план M13 G10) не выбран — в Spring Boot 3.4
 * attribute {@code url.path} устанавливается на span <em>после</em>
 * {@code Sampler#shouldSample}, поэтому sampling-time дроп для actuator
 * paths невозможен. Подробности — в {@link ActuatorTracingExcludeFilter}
 * Javadoc.
 */
@AutoConfiguration
@ConditionalOnClass(SpanExportingPredicate.class)
public class SharedObservabilityAutoConfiguration {

    /**
     * Без {@code @ConditionalOnMissingBean} — Spring Boot собирает <em>все</em>
     * {@link SpanExportingPredicate} bean'ы через {@code ObjectProvider}, не
     * требует уникальности (несколько фильтров складываются в AND).
     */
    @Bean
    public SpanExportingPredicate actuatorTracingExcludeFilter() {
        return new ActuatorTracingExcludeFilter();
    }
}
