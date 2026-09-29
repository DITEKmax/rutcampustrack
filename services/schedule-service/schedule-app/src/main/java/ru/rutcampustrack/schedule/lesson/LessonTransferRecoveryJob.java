package ru.rutcampustrack.schedule.lesson;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Republishes the immutable transfer command while its durable receipts are incomplete. */
@Component
@Profile("!test")
@Slf4j
public class LessonTransferRecoveryJob {

    private final LessonTransferWriter transferWriter;

    public LessonTransferRecoveryJob(LessonTransferWriter transferWriter) {
        this.transferWriter = transferWriter;
    }

    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "lesson-transfer-recovery",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT5S")
    public void republishIncompleteTransfers() {
        int published = transferWriter.republishPendingBatches();
        if (published > 0) {
            log.info("Republished {} pending lesson-transfer batches", published);
        }
    }
}
