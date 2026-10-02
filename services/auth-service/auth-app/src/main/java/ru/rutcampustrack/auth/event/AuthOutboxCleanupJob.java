package ru.rutcampustrack.auth.event;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.shared.outbox.OutboxCleanupJob;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Clock;

public class AuthOutboxCleanupJob extends OutboxCleanupJob {
    public AuthOutboxCleanupJob(OutboxStorage storage, Clock clock, int retentionDays) {
        super(storage, clock, retentionDays);
    }

    @Override
    @Scheduled(cron = "${rutcampustrack.auth-outbox.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(name = "auth-outbox-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void tick() {
        runCleanup();
    }
}
