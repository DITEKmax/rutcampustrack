package ru.rutcampustrack.shared.outbox;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.shared.events.IdempotencyStore;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyCleanupJobTest {

    @Test
    void runCleanup_callsDeleteWithCutoff() {
        Instant fixedNow = Instant.parse("2026-04-25T12:00:00Z");
        Clock clock = Clock.fixed(fixedNow, ZoneId.of("UTC"));
        CapturingStore store = new CapturingStore();

        IdempotencyCleanupJob job = new IdempotencyCleanupJob(store, clock, 7);

        long deleted = job.runCleanup();

        assertThat(deleted).isEqualTo(42L);
        assertThat(store.lastCutoff)
                .isEqualTo(fixedNow.minusSeconds(7L * 86400));
    }

    @Test
    void runCleanup_zeroDeleted_logsNothing() {
        IdempotencyCleanupJob job = new IdempotencyCleanupJob(
                new CapturingStore(0L), Clock.systemUTC(), 1);
        assertThat(job.runCleanup()).isZero();
    }

    @Test
    void retentionDays_mustBePositive() {
        assertThatThrownBy(() -> new IdempotencyCleanupJob(
                new CapturingStore(), Clock.systemUTC(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retentionDays");
    }

    static class CapturingStore implements IdempotencyStore {
        private final long deleteResult;
        Instant lastCutoff;
        final AtomicLong calls = new AtomicLong();

        CapturingStore() {
            this(42L);
        }

        CapturingStore(long deleteResult) {
            this.deleteResult = deleteResult;
        }

        @Override
        public boolean tryClaim(String consumerId, UUID eventId) {
            return true;
        }

        @Override
        public long deleteProcessedBefore(Instant before) {
            lastCutoff = before;
            calls.incrementAndGet();
            return deleteResult;
        }
    }
}
