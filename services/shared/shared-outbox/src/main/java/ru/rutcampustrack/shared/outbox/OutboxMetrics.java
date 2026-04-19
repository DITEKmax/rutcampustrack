package ru.rutcampustrack.shared.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Регистрирует Micrometer-метрики для outbox (M02 Группа 6).
 *
 * <p>Единственная публикуемая метрика — gauge {@code outbox.lag},
 * возвращающая число записей в статусе {@code pending}. Помогает
 * алертам: если lag устойчиво растёт — Rabbit недоступен / publisher
 * не успевает / ошибка в sender'е.
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
    }
}
