package ru.rutcampustrack.academic.homework;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Durable bounded batches; backoff never releases an unresolved Schedule gate. */
@Component
public class HomeworkEditRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(HomeworkEditRecoveryJob.class);
    private final HomeworkEditPersistence persistence;
    private final HomeworkEditCoordinator coordinator;
    public HomeworkEditRecoveryJob(HomeworkEditPersistence persistence, HomeworkEditCoordinator coordinator) {
        this.persistence = persistence; this.coordinator = coordinator;
    }
    @Scheduled(fixedDelayString = "${homework.edit-recovery.delay-ms:15000}")
    @SchedulerLock(name = "homeworkEditRecovery", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    public void recoverPending() {
        for (HomeworkEditOperation operation : persistence.pending()) {
            try { coordinator.recover(operation); }
            catch (RuntimeException failure) {
                persistence.retryLater(operation.operationId(), failure.getClass().getSimpleName());
                log.warn("Homework edit recovery pending operation={} homework={} cause={}",
                        operation.operationId(), operation.homeworkId(), failure.getClass().getSimpleName());
            }
        }
    }
}
