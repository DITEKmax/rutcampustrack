package ru.rutcampustrack.schedule.constructor;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bounded recovery of committed source admissions; never runs inside a business SQL transaction.
 */
@Component
public class ConstructorDeletionRecoveryJob {
    private final ConstructorDeletionStore store;
    private final ConstructorLifecycleCoordinator coordinator;

    public ConstructorDeletionRecoveryJob(
            ConstructorDeletionStore store, ConstructorLifecycleCoordinator coordinator) {
        this.store = store;
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(
            name = "constructor-deletion-recovery",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT1S")
    public void recover() {
        for (var id : store.recoverable()) coordinator.resume(id, 10_000);
    }
}
