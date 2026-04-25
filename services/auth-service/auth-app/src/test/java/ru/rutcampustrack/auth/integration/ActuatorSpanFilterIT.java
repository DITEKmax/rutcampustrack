package ru.rutcampustrack.auth.integration;

import io.micrometer.tracing.exporter.SpanExportingPredicate;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.shared.observability.ActuatorTracingExcludeFilter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M13 G10 — end-to-end IT для {@link ActuatorTracingExcludeFilter}.
 *
 * <p>Проверяет что Spring Boot tracing auto-config собирает наш filter в
 * {@code CompositeSpanExporter} (см. {@code OpenTelemetryTracingAutoConfiguration#otelSpanProcessor}),
 * и actuator spans (root + child через trace_id) не попадают в OTLP exporter.
 *
 * <p>Подход: добавляем {@link InMemorySpanExporter} как additional
 * {@link SpanExporter} bean. Default {@code BatchSpanProcessor} применяет
 * predicate filter перед {@code export()}. Force-flush через
 * {@link SdkTracerProvider#forceFlush()} убирает batch latency.
 */
@Import(ActuatorSpanFilterIT.SpanExporterTestConfig.class)
class ActuatorSpanFilterIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    InMemorySpanExporter inMemorySpanExporter;

    @Autowired
    SdkTracerProvider tracerProvider;

    @BeforeEach
    void resetExporter() {
        inMemorySpanExporter.reset();
    }

    @Test
    void filter_isActuatorTracingExcludeFilter_fromSharedAutoConfig() {
        Map<String, SpanExportingPredicate> beans =
                applicationContext.getBeansOfType(SpanExportingPredicate.class);

        assertThat(beans.values())
                .as("shared-observability auto-config должен регистрировать ActuatorTracingExcludeFilter bean")
                .anyMatch(bean -> bean instanceof ActuatorTracingExcludeFilter);
    }

    @Test
    void healthEndpoint_doesNotProduceAnyExportedSpan() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        flushAndWait();

        List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("ни один /actuator/health span не должен попасть в exporter " +
                        "(root отбрасывается по name+tags, child — по trace_id)")
                .noneMatch(ActuatorSpanFilterIT::spanReferencesActuator)
                .as("trace_id'ы actuator-trace также должны быть пусты")
                .allSatisfy(span -> assertThat(span.getTraceId())
                        .as("span %s имеет trace_id = trace actuator request'а", span.getName())
                        .satisfies(traceId -> assertThat(spans.stream()
                                .filter(s -> s.getTraceId().equals(traceId))
                                .anyMatch(ActuatorSpanFilterIT::spanReferencesActuator))
                                .isFalse()));
    }

    @Test
    void businessEndpoint_produces_spans_sanityCheck() {
        // POST /auth/login без credentials → 400/401, но spans создаются
        // и должны попасть в exporter (filter их пропускает).
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", "{}", String.class);
        assertThat(response.getStatusCode().value()).isGreaterThanOrEqualTo(400);

        flushAndWait();

        List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
        assertThat(spans)
                .as("бизнес-endpoint /auth/login должен создавать хотя бы один span")
                .isNotEmpty();
    }

    private void flushAndWait() {
        // BatchSpanProcessor по умолчанию ждёт 5 сек перед flush. Force-flush
        // вызывает экспорт всех pending spans синхронно (CompletableResultCode).
        tracerProvider.forceFlush().join(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static boolean spanReferencesActuator(SpanData span) {
        String name = span.getName();
        if (name != null && name.contains("/actuator")) {
            return true;
        }
        String urlPath = span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("url.path"));
        if (urlPath != null && urlPath.startsWith("/actuator")) {
            return true;
        }
        String httpUrl = span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("http.url"));
        if (httpUrl != null && httpUrl.contains("/actuator")) {
            return true;
        }
        String uri = span.getAttributes().get(
                io.opentelemetry.api.common.AttributeKey.stringKey("uri"));
        return uri != null && uri.startsWith("/actuator");
    }

    @TestConfiguration
    static class SpanExporterTestConfig {

        @Bean
        InMemorySpanExporter inMemorySpanExporter() {
            return InMemorySpanExporter.create();
        }

        /**
         * Spring Boot {@code OpenTelemetryTracingAutoConfiguration#otelSpanProcessor}
         * собирает все {@code SpanExporter} beans через {@code ObjectProvider} и
         * оборачивает их в {@code CompositeSpanExporter} с применением
         * {@code SpanExportingPredicate}. Наш bean добавляется к существующему
         * OTLP exporter'у; в тесте OTLP не сконфигурирован, потому что
         * {@code OTEL_EXPORTER_OTLP_ENDPOINT} не выставлен → noop.
         */
        @Bean
        SpanExporter inMemorySpanExporterAsExporter(InMemorySpanExporter exporter) {
            return exporter;
        }
    }
}
