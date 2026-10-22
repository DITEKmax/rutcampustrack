package ru.rutcampustrack.shared.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * M02 Группа 4 — unit-тесты {@link OutboxPublisherJob} с mock Storage и Sender. Проверяется только
 * логика tick'а (read → send → mark), без Spring-контекста.
 */
class OutboxPublisherJobTest {

    @Test
    void elapsedBatchBudgetCommitsOnlyTheConfirmedFifoPrefixAndLeavesRemainderPending() {
        OutboxStorage storage = mock(OutboxStorage.class);
        List<OutboxRecord> rows =
                java.util.stream.LongStream.rangeClosed(1, 5)
                        .mapToObj(
                                id ->
                                        new OutboxRecord(
                                                id,
                                                "lesson.closed",
                                                "{\"event_id\":" + id + "}",
                                                Instant.now()))
                        .toList();
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(rows);
        java.util.concurrent.atomic.AtomicLong elapsed =
                new java.util.concurrent.atomic.AtomicLong();
        List<Object> published = new ArrayList<>();
        OutboxPublisherJob job =
                new OutboxPublisherJob(
                        storage,
                        (type, payload) -> {
                            published.add(type + ":" + payload);
                            elapsed.addAndGet(java.util.concurrent.TimeUnit.SECONDS.toNanos(6));
                        },
                        null,
                        elapsed::get);

        assertThat(job.publishBatch()).isEqualTo(2);

        assertThat(published)
                .containsExactly(
                        "lesson.closed:{\"event_id\":1}", "lesson.closed:{\"event_id\":2}");
        verify(storage).markSent(1L);
        verify(storage).markSent(2L);
        verify(storage, never()).markSent(3L);
        verify(storage, never()).markSent(4L);
        verify(storage, never()).markSent(5L);
        verify(storage, never()).markFailed(any(), any());
    }

    @Test
    void brokerConfirmIsCountedSeparatelyAndRollbackDoesNotCountAsCommittedPublication() {
        OutboxStorage storage = mock(OutboxStorage.class);
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE))
                .thenReturn(List.of(new OutboxRecord(1L, "lesson.closed", "{}", Instant.now())));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxPublisherJob job = new OutboxPublisherJob(storage, (type, payload) -> {}, registry);
        TransactionTemplate tx =
                new TransactionTemplate(
                        new AbstractPlatformTransactionManager() {
                            @Override
                            protected Object doGetTransaction() {
                                return new Object();
                            }

                            @Override
                            protected void doBegin(
                                    Object transaction, TransactionDefinition definition) {}

                            @Override
                            protected void doCommit(DefaultTransactionStatus status) {}

                            @Override
                            protected void doRollback(DefaultTransactionStatus status) {}
                        });

        tx.executeWithoutResult(
                status -> {
                    job.publishBatch();
                    assertThat(registry.find("outbox.published.total").counter()).isNull();
                    status.setRollbackOnly();
                });
        assertThat(registry.find("outbox.published.total").counter()).isNull();
        assertThat(
                        registry.get("outbox.publish.attempts.total")
                                .tag("outcome", "confirmed")
                                .counter()
                                .count())
                .isEqualTo(1);

        tx.executeWithoutResult(status -> job.publishBatch());
        assertThat(registry.get("outbox.published.total").counter().count()).isEqualTo(1);
        assertThat(
                        registry.get("outbox.publish.attempts.total")
                                .tag("outcome", "confirmed")
                                .counter()
                                .count())
                .isEqualTo(2);
    }

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
        List<OutboxRecord> batch =
                List.of(
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
        doThrow(new RuntimeException("rabbit unavailable"))
                .when(sender)
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
        List<OutboxRecord> batch =
                List.of(
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
        List<String> sendOrder = new ArrayList<>();
        OutboxEventSender sender =
                (type, payload) -> {
                    sendOrder.add(type + ":" + payload);
                };
        List<OutboxRecord> batch =
                List.of(
                        new OutboxRecord(10L, "a", "event-10", Instant.now()),
                        new OutboxRecord(20L, "b", "event-20", Instant.now()),
                        new OutboxRecord(30L, "c", "event-30", Instant.now()));
        when(storage.findPending(OutboxPublisherJob.BATCH_SIZE)).thenReturn(batch);

        new OutboxPublisherJob(storage, sender).publishBatch();

        assertThat(sendOrder).containsExactly("a:event-10", "b:event-20", "c:event-30");
    }

    @Test
    void lockName_isConstant() {
        assertThat(OutboxPublisherJob.LOCK_NAME).isEqualTo("outbox-publisher");
    }
}
