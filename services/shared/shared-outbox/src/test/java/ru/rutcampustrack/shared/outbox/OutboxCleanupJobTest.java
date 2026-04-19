package ru.rutcampustrack.shared.outbox;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M02 Группа 6 — unit-тесты {@link OutboxCleanupJob}.
 */
class OutboxCleanupJobTest {

    private final Instant now = Instant.parse("2026-04-19T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneId.of("UTC"));

    @Test
    void runCleanup_callsDeleteSentBeforeWithCutoff() {
        OutboxStorage storage = mock(OutboxStorage.class);
        when(storage.deleteSentBefore(any())).thenReturn(5L);

        OutboxCleanupJob job = new OutboxCleanupJob(storage, fixedClock, 7);
        long deleted = job.runCleanup();

        assertThat(deleted).isEqualTo(5L);
        Instant expectedCutoff = now.minus(Duration.ofDays(7));
        verify(storage).deleteSentBefore(expectedCutoff);
    }

    @Test
    void runCleanup_zeroDeletes_noException() {
        OutboxStorage storage = mock(OutboxStorage.class);
        when(storage.deleteSentBefore(any())).thenReturn(0L);

        long deleted = new OutboxCleanupJob(storage, fixedClock, 7).runCleanup();

        assertThat(deleted).isZero();
    }

    @Test
    void constructor_invalidRetention_throws() {
        OutboxStorage storage = mock(OutboxStorage.class);
        assertThatThrownBy(() -> new OutboxCleanupJob(storage, fixedClock, 0))
                .isInstanceOf(IllegalArgumentException.class);
        verify(storage, never()).deleteSentBefore(any());
    }

    @Test
    void lockName_isConstant() {
        assertThat(OutboxCleanupJob.LOCK_NAME).isEqualTo("outbox-cleanup");
    }
}
