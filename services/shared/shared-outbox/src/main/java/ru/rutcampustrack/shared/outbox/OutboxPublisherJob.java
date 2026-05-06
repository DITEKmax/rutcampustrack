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
 * При transport exception запись не переводится в FAILED: job логирует сбой,
 * бросает exception наружу, и следующий tick снова подхватывает pending rows.
 *
 * <p>Важно: {@code markFailed} помечает запись FAILED (не pending), поэтому
 * для временной недоступности RabbitMQ он не используется. Семантика отправки —
 * at-least-once, consumers дедуплицируют повторные события по {@code event_id}.
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
        // Keep transport failures retryable: do not mark a row FAILED after a
        // single RabbitMQ outage. Consumers deduplicate repeated sends by event_id.
        for (OutboxRecord record : batch) {
            try {
                sender.send(record.eventType(), record.payload());
            } catch (Exception e) {
                log.error("Outbox publish failed: id={}, eventType={}, error={}",
                        record.id(), record.eventType(), e.getMessage());
                incrementFailed(record.eventType());
                throw new IllegalStateException("Outbox publish failed: id=" + record.id()
                        + ", eventType=" + record.eventType(), e);
            }
        }
        for (OutboxRecord record : batch) {
            storage.markSent(record.id());
            incrementPublished(record.eventType());
        }
        if (!batch.isEmpty()) {
            log.debug("Outbox tick: published={}, failed=0", batch.size());
        }
        return batch.size();
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
                .description("Количество неуспешных publish-попыток outbox-событий")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();
    }
}
