package ru.rutcampustrack.academic.identityoperation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.dashboard.DashboardScaleObservationService;

import java.util.List;

/** Eviction follows local commit, including final publication after the Auth catalog catches up. */
@Component
public class AcademicIdentityCacheInvalidator {
    private final CacheManager caches;
    private final ObjectProvider<DashboardScaleObservationService> observations;

    public AcademicIdentityCacheInvalidator(
            ObjectProvider<CacheManager> caches,
            ObjectProvider<DashboardScaleObservationService> observations) {
        this.caches = caches.getIfAvailable();
        this.observations = observations;
    }

    /**
     * Resume the existing durable CHANGE marker only after Auth publication has become readable.
     */
    public void afterPublicationCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("identity publication requires transaction");
        if (TransactionSynchronizationManager.getSynchronizations().stream()
                .anyMatch(PublicationCaptureCompletion.class::isInstance)) return;
        TransactionSynchronizationManager.registerSynchronization(
                new PublicationCaptureCompletion());
    }

    private final class PublicationCaptureCompletion implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            try {
                observations.getObject().capturePending();
            } catch (RuntimeException failure) {
                // Publication is already committed. Preserve the original unsettled marker,
                // including when another still-fenced operation prevents a coherent capture.
                org.slf4j.LoggerFactory.getLogger(AcademicIdentityCacheInvalidator.class)
                        .warn(
                                "Dashboard scale observation remains pending after identity"
                                    + " publication");
            }
        }
    }

    public void afterCommit(List<Long> users, List<Long> groups) {
        if (caches == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("identity cache eviction requires transaction");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        var userCache = caches.getCache("users");
                        if (userCache != null) users.forEach(userCache::evict);
                        var members = caches.getCache("group_members");
                        if (members != null) groups.forEach(members::evict);
                        var rbac = caches.getCache("rbac");
                        if (rbac != null)
                            for (Long user : users)
                                for (Long group : groups) rbac.evict(user + ":" + group);
                    }
                });
    }
}
