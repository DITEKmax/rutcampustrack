package ru.rutcampustrack.shared.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Публикует pending outbox-записи во внешний транспорт.
 *
 * <p>Запускается раз в {@code fixedDelay = 5000ms} (ms). {@link SchedulerLock} гарантирует, что при
 * scale-out только один инстанс за tick реально читает outbox. Имя lock'а — константа {@code
 * "outbox-publisher"}: каждый сервис держит свой ShedLock-table в своей БД (academic_db /
 * schedule_db — PG, attendance_db — Mongo), поэтому три сервиса не конкурируют за один lock.
 * Конкурируют только инстансы ОДНОГО сервиса — ровно то что нужно.
 *
 * <p>Транзакционность: {@code @Transactional} на tick — вся batch'а идёт одной tx. После успешной
 * publish-попытки sender'ом → {@code markSent}. При transport exception запись не переводится в
 * FAILED: job логирует сбой, бросает exception наружу, и следующий tick снова подхватывает pending
 * rows.
 *
 * <p>Важно: {@code markFailed} помечает запись FAILED (не pending), поэтому для временной
 * недоступности RabbitMQ он не используется. Семантика отправки — at-least-once, consumers
 * дедуплицируют повторные события по {@code event_id}.
 *
 * <p>Сервис-потребитель регистрирует этот job как {@code @Bean} и {@code @Component} сразу не нужен
 * — Spring сканирует shared-outbox пакет и создаёт сам (NEW-34 component-scan path).
 */
public class OutboxPublisherJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherJob.class);

    /** Максимальный размер batch'а за один tick. Защищает от OOM при backlog'е. */
    static final int BATCH_SIZE = 100;

    // Preserve fast-broker throughput while preventing 100 sequential 5s confirms from outliving
    // the 40s transaction / 60s scheduler lease. Stop starting sends after 10s, leaving room for
    // one bounded connect/handshake/channel/confirm/close and database completion. This is a wait
    // budget, not a wall-clock promise under thread suspension or host failure.
    static final long BATCH_BUDGET_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Имя lock'а в ShedLock-таблице. См. javadoc класса — константа by design. */
    public static final String LOCK_NAME = "outbox-publisher";

    private final OutboxStorage storage;
    private final OutboxEventSender sender;
    private final MeterRegistry meterRegistry;
    private final LongSupplier monotonicClock;

    /**
     * @param storage storage-реализация (JpaOutboxStorage / MongoOutboxStorage)
     * @param sender транспортный shim (обычно RabbitTemplate wrapper)
     */
    public OutboxPublisherJob(OutboxStorage storage, OutboxEventSender sender) {
        this(storage, sender, null);
    }

    /**
     * @param storage storage-реализация
     * @param sender транспортный shim
     * @param meterRegistry Micrometer registry — null допустимо (метрики не пишутся)
     */
    public OutboxPublisherJob(
            OutboxStorage storage, OutboxEventSender sender, MeterRegistry meterRegistry) {
        this(storage, sender, meterRegistry, System::nanoTime);
    }

    OutboxPublisherJob(
            OutboxStorage storage,
            OutboxEventSender sender,
            MeterRegistry meterRegistry,
            LongSupplier monotonicClock) {
        this.storage = storage;
        this.sender = sender;
        this.meterRegistry = meterRegistry;
        this.monotonicClock = monotonicClock;
    }

    @Scheduled(fixedDelayString = "${rutcampustrack.outbox.publisher.fixed-delay-ms:5000}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT1M", lockAtLeastFor = "PT5S")
    @Transactional(timeout = 40)
    public void tick() {
        publishBatch();
    }

    /**
     * Internal method — extracted для юнит-тестов без Spring proxy-wrapping. {@code @Transactional}
     * здесь не применяется — caller должен обеспечить tx.
     */
    public int publishBatch() {
        long started = monotonicClock.getAsLong();
        List<OutboxRecord> batch = storage.findPending(BATCH_SIZE);
        if (batch.isEmpty()) {
            return 0;
        }
        // Keep transport failures retryable: do not mark a row FAILED after a
        // single RabbitMQ outage. Consumers deduplicate repeated sends by event_id.
        List<OutboxRecord> confirmed = new ArrayList<>();
        for (OutboxRecord record : batch) {
            if (confirmed.size() >= BATCH_SIZE
                    || monotonicClock.getAsLong() - started >= BATCH_BUDGET_NANOS) {
                break;
            }
            try {
                sender.send(record.eventType(), record.payload());
                incrementAttempt("confirmed");
                confirmed.add(record);
            } catch (Exception e) {
                log.error("Outbox publish failed: id={}, category=transport", record.id());
                incrementAttempt("failed");
                incrementFailed(record.eventType());
                throw new IllegalStateException(
                        "Outbox publish failed; original event remains pending");
            }
        }
        for (OutboxRecord record : confirmed) {
            storage.markSent(record.id());
        }
        afterCommit(() -> confirmed.forEach(record -> incrementPublished(record.eventType())));
        if (!confirmed.isEmpty()) {
            log.debug("Outbox tick: published={}, failed=0", confirmed.size());
        }
        return confirmed.size();
    }

    private static void afterCommit(Runnable committed) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            committed.run();
                        }
                    });
        } else {
            committed.run();
        }
    }

    private void incrementAttempt(String outcome) {
        if (meterRegistry == null) return;
        Counter.builder("outbox.publish.attempts.total")
                .description("Transport attempts; confirmed does not imply local SENT commit")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private void incrementPublished(String eventType) {
        if (meterRegistry == null) return;
        Counter.builder("outbox.published.total")
                .description("Outbox SENT transitions committed after transport acceptance")
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
