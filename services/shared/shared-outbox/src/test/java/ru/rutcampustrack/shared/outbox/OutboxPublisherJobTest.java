package ru.rutcampustrack.shared.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M02 Группа 4 — unit-тесты {@link OutboxPublisherJob} с mock Storage и Sender.
 * Проверяется только логика tick'а (read → send → mark), без Spring-контекста.
 */
class OutboxPublisherJobTest {

    @Test
    void emptyBatch_doesNothing() {
        OutboxStorage storage = mock(OutboxStorage.class);
        OutboxEventSender sender = mock(OutboxEventSender.class);
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(List.of());

        OutboxPublisherJob job = new OutboxPublisherJob(storage, sender);
        int published = job.publishBatch();

        assertThat(published).isZero();
        verify(storage, never()).markSent(any());
        verify(storage, never()).markFailed(any(), any());
    }

    @Test
    void allSuccess_marksEachSent() throws Exception {
        OutboxStorage storage = mock(OutboxStorage.class);
        OutboxEventSender sender = mock(OutboxEventSender.class);
        List<OutboxRecord> batch = List.of(
                new OutboxRecord(1L, "lesson.started", "{\"id\":1}", Instant.now()),
                new OutboxRecord(2L, "lesson.closed", "{\"id\":2}", Instant.now()));
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(batch);

        OutboxPublisherJob job = new OutboxPublisherJob(storage, sender);
        int published = job.publishBatch();

        assertThat(published).isEqualTo(2);
        verify(sender).send("lesson.started", "{\"id\":1}");
        verify(sender).send("lesson.closed", "{\"id\":2}");
        verify(storage).markSent(1L);
        verify(storage).markSent(2L);
        verify(storage, never()).markFailed(any(), any());
    }

    @Test
    void senderThrows_keepsPendingForRetry() throws Exception {
        OutboxStorage storage = mock(OutboxStorage.class);
        OutboxEventSender sender = mock(OutboxEventSender.class);
        OutboxRecord rec = new OutboxRecord(42L, "attendance.marked", "{}", Instant.now());
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(List.of(rec));
        doThrow(new RuntimeException("rabbit unavailable")).when(sender)
                .send("attendance.marked", "{}");

        OutboxPublisherJob job = new OutboxPublisherJob(storage, sender);

        assertThatThrownBy(job::publishBatch)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox publish failed");

        verify(storage, never()).markFailed(any(), any());
        verify(storage, never()).markSent(any());
    }

    @Test
    void partialFailure_stopsAndLeavesWholeBatchPending() throws Exception {
        OutboxStorage storage = mock(OutboxStorage.class);
        OutboxEventSender sender = mock(OutboxEventSender.class);
        List<OutboxRecord> batch = List.of(
                new OutboxRecord(1L, "ok", "{}", Instant.now()),
                new OutboxRecord(2L, "bad", "{}", Instant.now()),
                new OutboxRecord(3L, "later", "{}", Instant.now()));
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(batch);
        doThrow(new RuntimeException("broken payload")).when(sender).send("bad", "{}");

        OutboxPublisherJob job = new OutboxPublisherJob(storage, sender);

        assertThatThrownBy(job::publishBatch)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Outbox publish failed");

        verify(sender).send("ok", "{}");
        verify(sender).send("bad", "{}");
        verify(sender, never()).send("later", "{}");
        verify(storage, never()).markFailed(any(), any());
        verify(storage, never()).markSent(any());
    }

    @Test
    void sendOrder_matchesReadOrder() throws Exception {
        OutboxStorage storage = mock(OutboxStorage.class);
        AtomicInteger callOrder = new AtomicInteger();
        List<Integer> sendOrder = new ArrayList<>();
        OutboxEventSender sender = (type, payload) -> {
            sendOrder.add(callOrder.incrementAndGet());
        };
        List<OutboxRecord> batch = List.of(
                new OutboxRecord(10L, "a", "{}", Instant.now()),
                new OutboxRecord(20L, "b", "{}", Instant.now()),
                new OutboxRecord(30L, "c", "{}", Instant.now()));
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(batch);

        new OutboxPublisherJob(storage, sender).publishBatch();

        assertThat(sendOrder).containsExactly(1, 2, 3);
    }

    @Test
    void lockName_isConstant() {
        assertThat(OutboxPublisherJob.LOCK_NAME).isEqualTo("outbox-publisher");
    }
}
