package ru.rutcampustrack.shared.outbox;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Удаляет SENT outbox-записи старше N дней (retention policy, NEW-7).
 *
 * <p>Запускается раз в сутки в 03:00 ({@code cron = "0 0 3 * * *"}) —
 * низкая нагрузка, off-peak. Конкурентность между инстансами блокируется
 * {@link SchedulerLock} с именем {@code outbox-cleanup}.
 *
 * <p>Retention — 7 дней по умолчанию. Конфигурируется через свойство
 * {@code rutcampustrack.outbox.retention-days} (см. {@code @Scheduled} для
 * конфига cron через application.yml не используем — выполняется раз в
 * сутки, точного времени не требуется).
 *
 * <p>{@link Clock} параметр позволяет тестам подменять «сейчас» — integration
 * тест cleanup'а (Группа 6 acceptance) перематывает часы через @MockBean.
 */
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    /** Имя lock'а в ShedLock-таблице. */
    public static final String LOCK_NAME = "outbox-cleanup";

    private final OutboxStorage storage;
    private final Clock clock;
    private final Duration retention;

    /**
     * @param storage       storage-реализация
     * @param clock         часы — для тестируемости
     * @param retentionDays retention в днях (NEW-7: 7 дней по умолчанию)
     */
    public OutboxCleanupJob(OutboxStorage storage, Clock clock, int retentionDays) {
        if (retentionDays < 1) {
            throw new IllegalArgumentException("retentionDays must be >= 1, got " + retentionDays);
        }
        this.storage = storage;
        this.clock = clock;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(cron = "${rutcampustrack.outbox.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void tick() {
        runCleanup();
    }

    /**
     * Internal method — extracted для юнит-тестов без Spring proxy.
     */
    public long runCleanup() {
        Instant cutoff = Instant.now(clock).minus(retention);
        long deleted = storage.deleteSentBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} sent rows older than {}", deleted, cutoff);
        }
        return deleted;
    }
}
