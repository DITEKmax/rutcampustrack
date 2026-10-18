package ru.rutcampustrack.academic.groupavailability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;

/** Replays fixed commands and receipts; neither an expired bearer nor a timer releases a guard. */
@Component
@Profile("!test")
public class GroupAvailabilityRecovery {
    private static final Logger LOG = LoggerFactory.getLogger(GroupAvailabilityRecovery.class);
    private final GroupAvailabilityStore store;
    private final GroupAvailabilityCoordinator coordinator;

    public GroupAvailabilityRecovery(
            GroupAvailabilityStore store, GroupAvailabilityCoordinator coordinator) {
        this.store = store;
        this.coordinator = coordinator;
    }

    @Scheduled(
            fixedDelayString = "${academic.identity-operation.recovery-delay-ms:2000}",
            initialDelayString = "${academic.identity-operation.recovery-initial-delay-ms:10000}")
    @SingleInstanceScheduled(
            reason =
                    "Single worker retries fixed group intents; existing scope locks, phase CAS and"
                        + " Auth receipts preserve pending group guards.")
    public void recover() {
        for (var id : store.pending())
            try {
                coordinator.recover(id);
            } catch (RuntimeException pending) {
                store.retryLater(id);
                LOG.warn(
                        "Group lifecycle recovery deferred; fixed intent and scope guards"
                                + " retained");
            }
    }
}
