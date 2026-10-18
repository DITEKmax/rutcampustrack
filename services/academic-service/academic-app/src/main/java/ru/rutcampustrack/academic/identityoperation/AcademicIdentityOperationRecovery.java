package ru.rutcampustrack.academic.identityoperation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;

/** Bounded recovery uses stored accepted commands and receipts, never a retained bearer token. */
@Component
@Profile("!test")
public class AcademicIdentityOperationRecovery {
    private static final Logger LOG =
            LoggerFactory.getLogger(AcademicIdentityOperationRecovery.class);
    private final JdbcAcademicIdentityOperationStore store;
    private final AcademicIdentityCoordinator coordinator;

    public AcademicIdentityOperationRecovery(
            JdbcAcademicIdentityOperationStore store, AcademicIdentityCoordinator coordinator) {
        this.store = store;
        this.coordinator = coordinator;
    }

    @Scheduled(
            fixedDelayString = "${academic.identity-operation.recovery-delay-ms:2000}",
            initialDelayString = "${academic.identity-operation.recovery-initial-delay-ms:10000}")
    @SingleInstanceScheduled(
            reason =
                    "Single worker retries persisted operation IDs; own-row phase CAS and exact"
                        + " Auth receipts retain fences on unknown outcomes.")
    public void recover() {
        for (String id : store.pending()) {
            try {
                coordinator.recover(id);
            } catch (RuntimeException deferred) {
                store.retryLater(id);
                LOG.warn(
                        "Identity operation recovery deferred; accepted state and fences retained");
            }
        }
    }
}
