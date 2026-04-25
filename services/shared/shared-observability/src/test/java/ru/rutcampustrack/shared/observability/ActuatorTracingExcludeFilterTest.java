package ru.rutcampustrack.shared.observability;

import io.micrometer.tracing.exporter.FinishedSpan;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ActuatorTracingExcludeFilterTest {

    private final ActuatorTracingExcludeFilter filter = new ActuatorTracingExcludeFilter();

    @Test
    void drops_actuatorRoot_byName() {
        FinishedSpan span = stubSpan("trace-1", "http get /actuator/health", Map.of());

        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void drops_actuatorRoot_byUrlPathTag() {
        // Spring Boot 3.4+ semconv 1.20+
        FinishedSpan span = stubSpan("trace-2", "HTTP GET", Map.of("url.path", "/actuator/prometheus"));

        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void drops_actuatorRoot_byUriTag() {
        // Spring Boot WebMvc Brave-style observation tags
        FinishedSpan span = stubSpan("trace-3", "HTTP GET", Map.of("uri", "/actuator/info"));

        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void drops_actuatorRoot_byHttpUrlTag() {
        FinishedSpan span = stubSpan("trace-4", "HTTP GET",
                Map.of("http.url", "http://localhost:9090/actuator/health"));

        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void drops_actuatorRoot_byLegacyHttpTargetTag() {
        FinishedSpan span = stubSpan("trace-5", "HTTP GET", Map.of("http.target", "/actuator/health"));

        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void drops_childSpans_when_traceIdMatches_droppedActuatorRoot() {
        // Root: actuator http span
        FinishedSpan root = stubSpan("trace-child", "http get /actuator/health", Map.of());
        // Child: spring-security observation, нет URL info, тот же trace_id
        FinishedSpan child1 = stubSpan("trace-child", "authorize request", Map.of());
        FinishedSpan child2 = stubSpan("trace-child", "secured request", Map.of());

        assertThat(filter.isExportable(root)).isFalse();
        assertThat(filter.isExportable(child1)).isFalse();
        assertThat(filter.isExportable(child2)).isFalse();
    }

    @Test
    void passes_businessSpan_byName() {
        FinishedSpan span = stubSpan("trace-business", "http post /api/auth/login", Map.of());

        assertThat(filter.isExportable(span)).isTrue();
    }

    @Test
    void passes_businessSpan_byUrlPath() {
        FinishedSpan span = stubSpan("trace-biz-2", "HTTP POST",
                Map.of("url.path", "/api/auth/login"));

        assertThat(filter.isExportable(span)).isTrue();
    }

    @Test
    void passes_substringMatch_isNotConfusedWithActuator() {
        // /api/foo/actuator-status НЕ должен дропаться — только /actuator/ prefix
        FinishedSpan span = stubSpan("trace-biz-3", "HTTP GET",
                Map.of("url.path", "/api/foo/actuator-status"));

        assertThat(filter.isExportable(span)).isTrue();
    }

    @Test
    void passes_internalSpan_withUnknownTrace() {
        // Internal span, нет path info, никогда не видели его trace_id
        FinishedSpan span = stubSpan("trace-internal", "scheduled.cleanup", Map.of());

        assertThat(filter.isExportable(span)).isTrue();
    }

    @Test
    void lru_evicts_oldestTraceId_atCapacity() {
        ActuatorTracingExcludeFilter smallFilter = new ActuatorTracingExcludeFilter(2);

        // Заполняем cache 2 actuator trace'ами
        smallFilter.isExportable(stubSpan("t1", "http get /actuator/health", Map.of()));
        smallFilter.isExportable(stubSpan("t2", "http get /actuator/info", Map.of()));

        // 3-й actuator hit вытесняет t1
        smallFilter.isExportable(stubSpan("t3", "http get /actuator/prometheus", Map.of()));

        // child от t1 теперь exportable (cache его забыл)
        FinishedSpan staleChild = stubSpan("t1", "authorize request", Map.of());
        assertThat(smallFilter.isExportable(staleChild)).isTrue();

        // child от t2 и t3 всё ещё дропается
        assertThat(smallFilter.isExportable(stubSpan("t2", "authorize request", Map.of()))).isFalse();
        assertThat(smallFilter.isExportable(stubSpan("t3", "authorize request", Map.of()))).isFalse();
    }

    @Test
    void handles_nullTraceId_gracefully() {
        FinishedSpan span = stubSpan(null, "http get /actuator/health", Map.of());

        // Должен дропнуться по name, без NPE на null traceId
        assertThat(filter.isExportable(span)).isFalse();
    }

    @Test
    void handles_nullName_andEmptyTags() {
        FinishedSpan span = stubSpan("trace-empty", null, Map.of());

        // Без name и без tags — open-by-default, пропускаем
        assertThat(filter.isExportable(span)).isTrue();
    }

    private static FinishedSpan stubSpan(String traceId, String name, Map<String, String> tags) {
        FinishedSpan span = mock(FinishedSpan.class);
        when(span.getTraceId()).thenReturn(traceId);
        when(span.getName()).thenReturn(name);
        when(span.getTags()).thenReturn(tags);
        return span;
    }
}
