package ru.rutcampustrack.academic.map;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes pseudonymous map-open retention rows after both database-defined
 * deadlines.  The daily floor aggregate is intentionally left untouched.
 */
public class CampusMapUsageCleanupJob {
    public static final String LOCK_NAME = "campus-map-usage-cleanup";

    private static final Logger log = LoggerFactory.getLogger(CampusMapUsageCleanupJob.class);

    private final CampusMapUsageRepository repository;

    public CampusMapUsageCleanupJob(CampusMapUsageRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${campus-map.usage.cleanup.cron:0 0 4 * * *}")
    @SchedulerLock(name = LOCK_NAME, lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void tick() {
        runCleanup();
    }

    /** Direct entry point for bounded integration checks. */
    public CleanupResult runCleanup() {
        int dedupeRows = repository.deleteExpiredDemandDedupe();
        int intentRows = repository.deleteExpiredOpenIntents();
        CleanupResult result = new CleanupResult(dedupeRows, intentRows);
        if (result.total() > 0) {
            log.info("Campus map usage cleanup: deleted {} dedupe rows and {} intent rows",
                    dedupeRows, intentRows);
        }
        return result;
    }

    public record CleanupResult(int dedupeRows, int intentRows) {
        public int total() {
            return dedupeRows + intentRows;
        }
    }
}
