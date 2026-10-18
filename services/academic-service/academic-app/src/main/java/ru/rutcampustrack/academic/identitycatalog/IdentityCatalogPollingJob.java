package ru.rutcampustrack.academic.identitycatalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class IdentityCatalogPollingJob {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityCatalogPollingJob.class);
    private final IdentityCatalogSynchronizer catalog;

    public IdentityCatalogPollingJob(IdentityCatalogSynchronizer catalog) {
        this.catalog = catalog;
    }

    @Scheduled(
            fixedDelayString = "${academic.identity-catalog.poll-delay-ms:2000}",
            initialDelayString = "${academic.identity-catalog.initial-delay-ms:10000}")
    public void poll() {
        try {
            catalog.poll();
        } catch (RuntimeException deferred) {
            LOG.warn("Identity catalog catch-up deferred; query freshness barriers remain closed");
        }
    }
}
