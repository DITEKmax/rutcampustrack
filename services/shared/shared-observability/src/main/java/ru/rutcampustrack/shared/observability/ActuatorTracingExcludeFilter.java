package ru.rutcampustrack.shared.observability;

import io.micrometer.tracing.exporter.FinishedSpan;
import io.micrometer.tracing.exporter.SpanExportingPredicate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * M13 G10 — фильтрует {@code /actuator/**} spans на этапе экспорта в OTLP.
 *
 * <p>Применяется через {@link SpanExportingPredicate} bean — Spring Boot 3.x
 * auto-config ({@code OpenTelemetryTracingAutoConfiguration#otelSpanProcessor})
 * собирает все predicate'ы в {@code CompositeSpanExporter} и применяет их
 * к каждому {@link FinishedSpan} до отправки в OTLP exporter.
 *
 * <p>Почему не {@code Sampler}: в Spring Boot 3.4 + Micrometer Bridge attribute
 * {@code url.path} / {@code http.target} устанавливается на span <em>после</em>
 * вызова {@code Sampler#shouldSample}, поэтому sampler-подход не видит path
 * для root http server span. {@code SpanExportingPredicate} видит финальный
 * span со всеми tags и финализированным name (вид {@code "http get /actuator/health"}).
 *
 * <p>Стратегия дропа:
 * <ol>
 *   <li><b>Root http server span</b> — фильтр по name содержащему {@code /actuator/}.
 *       Дополнительно проверяем tags ({@code uri}, {@code http.url}, {@code url.path},
 *       {@code http.target}) для устойчивости к смене instrumentation naming convention.</li>
 *   <li><b>Child internal spans</b> (security filterchain, authorize request, JPA
 *       queries — всё что Spring/Hibernate генерирует внутри request) —
 *       дропаются по {@code trace_id}: запоминаем trace_id отброшенного root,
 *       все последующие spans того же trace отбрасываем.</li>
 * </ol>
 *
 * <p>State: thread-safe LRU set из {@code traceIdLruCapacity} последних
 * actuator trace_id (default 256). Старые trace_id вытесняются при
 * заполнении — actuator hits не накапливают memory bloat. Capacity
 * подобран под 5 сервисов × actuator probe (Prometheus 30s interval +
 * health probes) ≈ 30-50 trace_id за 5 минут — 256 даёт большой запас.
 */
public final class ActuatorTracingExcludeFilter implements SpanExportingPredicate {

    private static final int DEFAULT_LRU_CAPACITY = 256;
    private static final String ACTUATOR_PREFIX = "/actuator/";
    private static final String ACTUATOR_ROOT = "/actuator";

    /**
     * Tag-ключи, которые могут содержать request path. Порядок не важен —
     * span должен содержать хотя бы один с {@code /actuator/} prefix чтобы
     * считаться actuator. Покрываем semconv 1.20+ ({@code url.path}),
     * legacy ({@code http.target}, {@code http.url}) и Brave ({@code uri}).
     */
    private static final String[] PATH_TAG_KEYS = {"url.path", "http.target", "http.url", "uri"};

    private final Set<String> droppedTraceIds;

    public ActuatorTracingExcludeFilter() {
        this(DEFAULT_LRU_CAPACITY);
    }

    ActuatorTracingExcludeFilter(int lruCapacity) {
        this.droppedTraceIds = Collections.synchronizedSet(new LruSet(lruCapacity));
    }

    @Override
    public boolean isExportable(FinishedSpan span) {
        String traceId = span.getTraceId();

        if (traceId != null && droppedTraceIds.contains(traceId)) {
            return false;
        }

        if (isActuator(span)) {
            if (traceId != null) {
                droppedTraceIds.add(traceId);
            }
            return false;
        }

        return true;
    }

    private static boolean isActuator(FinishedSpan span) {
        String name = span.getName();
        if (name != null && (name.contains(ACTUATOR_PREFIX) || name.endsWith(ACTUATOR_ROOT))) {
            return true;
        }
        var tags = span.getTags();
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        for (String key : PATH_TAG_KEYS) {
            String value = tags.get(key);
            if (value != null && (value.startsWith(ACTUATOR_PREFIX) || value.equals(ACTUATOR_ROOT)
                    || value.contains(ACTUATOR_PREFIX))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Простая LRU-семантика через {@link LinkedHashSet} access-order.
     * Synchronized wrapper применяется в конструкторе родителя.
     */
    private static final class LruSet extends LinkedHashSet<String> {
        private final int capacity;

        LruSet(int capacity) {
            super(capacity, 0.75f);
            this.capacity = capacity;
        }

        @Override
        public boolean add(String e) {
            boolean added = super.add(e);
            if (added && size() > capacity) {
                var iterator = iterator();
                iterator.next();
                iterator.remove();
            }
            return added;
        }
    }
}
