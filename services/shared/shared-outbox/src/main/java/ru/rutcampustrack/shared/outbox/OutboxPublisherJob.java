package ru.rutcampustrack.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Публикует pending outbox-записи во внешний транспорт.
 *
 * <p>Запускается раз в {@code fixedDelay = 5000ms} (ms). {@link SchedulerLock}
 * гарантирует, что при scale-out только один инстанс за tick реально читает
 * outbox. Имя lock'а — константа {@code "outbox-publisher"}: каждый сервис
 * держит свой ShedLock-table в своей БД (academic_db / schedule_db — PG,
 * attendance_db — Mongo), поэтому три сервиса не конкурируют за один lock.
 * Конкурируют только инстансы ОДНОГО сервиса — ровно то что нужно.
 *
 * <p>Транзакционность: {@code @Transactional} на tick — вся batch'а идёт
 * одной tx. После успешной publish-попытки sender'ом → {@code markSent}.
 * При exception → {@code markFailed} + log. Следующий tick подхватит если
 * row остался pending (Rabbit был недоступен — sender бросит exception,
 * status останется pending — repeat).
 *
 * <p>Важно: {@code markFailed} помечает запись FAILED (не pending) —
 * такой row больше не будет подобран. Retry-стратегия «вечных pending'ов»
 * при Rabbit outage работает через sender: sender бросает, tx откатывается,
 * row остаётся pending (markSent/markFailed в той же tx не успел вызваться).
 *
 * <p>Сервис-потребитель регистрирует этот job как {@code @Bean} и
 * {@code @Component} сразу не нужен — Spring сканирует shared-outbox пакет
 * и создаёт сам (NEW-34 component-scan path).
 */
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

    /** Максимальный размер batch'а за один tick. Защищает от OOM при backlog'е. */
    static final int BATCH_SIZE = 100;

    /** Имя lock'а в ShedLock-таблице. См. javadoc класса — константа by design. */
    public static final String LOCK_NAME = "outbox-publisher";

    private final OutboxStorage storage;
    private final OutboxEventSender sender;
    private final MeterRegistry meterRegistry;

    /**
     * @param storage storage-реализация (JpaOutboxStorage / MongoOutboxStorage)
     * @param sender  транспортный shim (обычно RabbitTemplate wrapper)
     */
    public OutboxPublisherJob(OutboxStorage storage, OutboxEventSender sender) {
        this(storage, sender, null);
    }

    /**
     * @param storage       storage-реализация
     * @param sender        транспортный shim
     * @param meterRegistry Micrometer registry — null допустимо (метрики не пишутся)
     */
    public OutboxPublisherJob(OutboxStorage storage, OutboxEventSender sender,
                              MeterRegistry meterRegistry) {
        this.storage = storage;
        this.sender = sender;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${rutcampustrack.outbox.publisher.fixed-delay-ms:5000}")
    @SchedulerLock(name = LOCK_NAME,
                   lockAtMostFor = "PT1M",
                   lockAtLeastFor = "PT5S")
    @Transactional
    public void tick() {
        publishBatch();
    }

    /**
     * Internal method — extracted для юнит-тестов без Spring proxy-wrapping.
     * {@code @Transactional} здесь не применяется — caller должен обеспечить tx.
     */
    public int publishBatch() {
        List<OutboxRecord> batch = storage.findPending(BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        for (OutboxRecord record : batch) {
            try {
                sender.send(record.eventType(), record.payload());
                storage.markSent(record.id());
                successCount++;
                incrementPublished(record.eventType());
            } catch (Exception e) {
                log.error("Outbox publish failed: id={}, eventType={}, error={}",
                        record.id(), record.eventType(), e.getMessage());
                storage.markFailed(record.id(), e.getMessage());
                incrementFailed(record.eventType());
            }
        }
        if (successCount > 0) {
            log.debug("Outbox tick: published={}, failed={}",
                    successCount, batch.size() - successCount);
        }
        return successCount;
    }

    private void incrementPublished(String eventType) {
        if (meterRegistry == null) return;
        Counter.builder("outbox.published.total")
                .description("Количество outbox-событий, успешно опубликованных в транспорт")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }

    private void incrementFailed(String eventType) {
        if (meterRegistry == null) return;
        Counter.builder("outbox.failed.total")
                .description("Количество outbox-событий, помеченных FAILED (sender threw)")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
