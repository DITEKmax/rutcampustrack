package ru.rutcampustrack.shared.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.events.IdempotencyStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Удаляет записи {@code event_consumer_processed} старше N дней
 * (M13 G8 retention policy).
 *
 * <p>Запускается раз в сутки в 03:30 ({@code cron = "0 30 3 * * *"}) —
 * через 30 минут после {@code OutboxCleanupJob}, чтобы не пересекаться
 * по lock'ам и ресурсам. Конкурентность между инстансами блокируется
 * {@link SchedulerLock} с именем {@code idempotency-cleanup}.
 *
 * <p>Retention 7 дней по умолчанию. RabbitMQ не хранит сообщения дольше
 * (DLQ TTL + autoAck), так что событие старше 7 дней не редоставится —
 * dedup-row безопасно удалить.
 *
 * <p>Конфигурируется через свойство
 * {@code rutcampustrack.idempotency.retention-days}.
 */
public class IdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    /** Имя lock'а в ShedLock-таблице. */
    public static final String LOCK_NAME = "idempotency-cleanup";

    private final IdempotencyStore store;
    private final Clock clock;
    private final Duration retention;

    public IdempotencyCleanupJob(IdempotencyStore store, Clock clock, int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1, got " + retentionDays);
        }
        this.store = store;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${rutcampustrack.idempotency.cleanup.cron:0 30 3 * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void tick() {
        runCleanup();
    }

    /** Internal — extracted for unit-tests без Spring proxy. */
    public long runCleanup() {
        Instant cutoff = Instant.now(clock).minus(retention);
        long deleted = store.deleteProcessedBefore(cutoff);
        if (deleted > 0) {
            log.info("Idempotency cleanup: deleted {} consumer-event rows older than {}",
                    deleted, cutoff);
        }
        return deleted;
    }
}
