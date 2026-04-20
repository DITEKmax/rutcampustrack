package ru.rutcampustrack.shared.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import ru.rutcampustrack.shared.observability.MetricNames;

/**
 * Регистрирует Micrometer-метрики для outbox (M02 Группа 6 + M04 Группа 8).
 *
 * <p>Две публикуемые метрики:
 * <ul>
 *   <li>{@code outbox.lag} — число pending записей (legacy M02 name).</li>
 *   <li>{@link MetricNames#OUTBOX_LAG_SECONDS} — возраст самого старого
 *       pending-события в секундах. Прямой сигнал «как далеко rabbit
 *       откатился»: countPending=5 может быть нормой (batch не забран),
 *       но age=300с это уже 5-минутный lag → alert.</li>
 * </ul>
 *
 * <p>Counter'ы {@code outbox.published.total} / {@code outbox.failed.total}
 * пишутся внутри {@link OutboxPublisherJob} (там где происходит событие).
 *
 * <p>Регистрация через конструктор — сервис-потребитель инстанцирует как
 * {@code @Bean}, Spring сам выполнит регистрацию gauge при создании bean'а.
 */
public class OutboxMetrics {

    public OutboxMetrics(OutboxStorage storage, MeterRegistry meterRegistry) {
        Gauge.builder("outbox.lag", storage, OutboxStorage::countPending)
                .description("Число pending outbox-записей (непубликованных в транспорт)")
                .register(meterRegistry);

        Gauge.builder(MetricNames.OUTBOX_LAG_SECONDS, storage,
                        s -> (double) s.oldestPendingAgeSeconds())
                .description("Возраст самого старого PENDING-события в секундах")
                .register(meterRegistry);
    }
}
