package ru.rutcampustrack.academic.identitycatalog;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/** A bounded feed catch-up; neither waiting nor Auth RPCs hold a local DB transaction. */
@Component
public class IdentityCatalogSynchronizer {
    private final AuthIdentityCatalogClient auth;
    private final JdbcIdentityCatalogProjection projection;

    public IdentityCatalogSynchronizer(
            AuthIdentityCatalogClient auth, JdbcIdentityCatalogProjection projection) {
        this.auth = auth;
        this.projection = projection;
    }

    public void await(long required, long deadlineNanos) {
        if (required < 0 || TransactionSynchronizationManager.isActualTransactionActive())
            throw AuthIdentityCatalogClient.unavailable();
        long cursor = projection.frontier();
        while (cursor < required) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0) throw AuthIdentityCatalogClient.unavailable();
            var changes = auth.changes(cursor, remaining);
            long next = projection.apply(changes);
            if (next <= cursor) throw AuthIdentityCatalogClient.unavailable();
            cursor = next;
        }
    }

    public long watermark(long deadlineNanos) {
        return auth.watermark(deadlineNanos - System.nanoTime());
    }

    public void poll() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        await(watermark(deadline), deadline);
    }
}
