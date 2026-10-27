package ru.rutcampustrack.attendance.marking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.rutcampustrack.attendance.exception.AttendanceWriteBusyException;

class MarkingWriteRetryTest {
    @AfterEach
    void clearThreadState() {
        TransactionSynchronizationManager.clear();
        Thread.interrupted();
    }

    private static MongoException abort() {
        MongoException abort = new MongoException(112, "owned abort");
        abort.addLabel("TransientTransactionError");
        return abort;
    }

    @Test
    void onlyCode112WithTransientLabelInCauseChainIsConfirmed() {
        assertThat(
                        MarkingWriteRetry.isConfirmedTransientAbort(
                                new DataIntegrityViolationException("translated", abort())))
                .isTrue();
        assertThat(
                        MarkingWriteRetry.isConfirmedTransientAbort(
                                new MongoException(112, "unlabelled")))
                .isFalse();
        MongoException otherCode = new MongoException(91, "transport");
        otherCode.addLabel("TransientTransactionError");
        assertThat(MarkingWriteRetry.isConfirmedTransientAbort(otherCode)).isFalse();
    }

    @Test
    void unknownCommitAnywhereVetoesReplayEvenIfAnotherCauseIsConfirmed() {
        MongoException unknown = new MongoException(112, "unknown");
        unknown.addLabel("UnknownTransactionCommitResult");
        unknown.initCause(abort());
        assertThat(MarkingWriteRetry.isConfirmedTransientAbort(unknown)).isFalse();
        MongoException outerAbort = abort();
        outerAbort.initCause(unknown);
        assertThat(MarkingWriteRetry.isConfirmedTransientAbort(outerAbort)).isFalse();
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                MarkingWriteRetry.execute(
                                        () -> {
                                            calls.incrementAndGet();
                                            throw outerAbort;
                                        }))
                .isSameAs(outerAbort);
        assertThat(calls).hasValue(1);
    }

    @Test
    void cyclicOrTruncatedCauseChainFailsClosed() {
        MongoException first = abort();
        RuntimeException second = new RuntimeException(first);
        first.initCause(second);
        assertThat(MarkingWriteRetry.isConfirmedTransientAbort(first)).isFalse();
        Throwable deep = abort();
        for (int i = 0; i < 64; i++) deep = new RuntimeException(deep);
        assertThat(MarkingWriteRetry.isConfirmedTransientAbort(deep)).isFalse();
    }

    @Test
    void confirmedAbortCanRetryAndReturnOnlySuccessfulInvocation() {
        AtomicInteger calls = new AtomicInteger();
        assertThat(
                        MarkingWriteRetry.execute(
                                () -> {
                                    if (calls.incrementAndGet() == 1) throw abort();
                                    return "committed";
                                }))
                .isEqualTo("committed");
        assertThat(calls).hasValue(2);
    }

    @Test
    void exhaustionIsFourAttemptsAndTypedBusyWithLastCause() {
        AtomicInteger calls = new AtomicInteger();
        MongoException failure = abort();
        assertThatThrownBy(
                        () ->
                                MarkingWriteRetry.execute(
                                        () -> {
                                            calls.incrementAndGet();
                                            throw failure;
                                        }))
                .isInstanceOf(AttendanceWriteBusyException.class)
                .hasCause(failure);
        assertThat(calls).hasValue(4);
    }

    @Test
    void ordinaryFailuresAreNotRetried() {
        for (RuntimeException failure :
                java.util.List.of(
                        new DataIntegrityViolationException("ordinary"),
                        new DuplicateKeyException("duplicate"),
                        new ru.rutcampustrack.attendance.exception.ConflictException(
                                "revision/deletion"),
                        new ru.rutcampustrack.attendance.exception.AccessDeniedException(
                                "permission"),
                        new MongoException(6, "transport"))) {
            AtomicInteger calls = new AtomicInteger();
            assertThatThrownBy(
                            () ->
                                    MarkingWriteRetry.execute(
                                            () -> {
                                                calls.incrementAndGet();
                                                throw failure;
                                            }))
                    .isSameAs(failure);
            assertThat(calls).hasValue(1);
        }
    }

    @Test
    void ambientTransactionRemainsOwnedByCallerAndIsNotRetried() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        AtomicInteger calls = new AtomicInteger();
        MongoException failure = abort();
        assertThatThrownBy(
                        () ->
                                MarkingWriteRetry.execute(
                                        () -> {
                                            calls.incrementAndGet();
                                            throw failure;
                                        }))
                .isSameAs(failure);
        assertThat(calls).hasValue(1);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
    }

    @Test
    void interruptedBackoffPreservesFlagAndDoesNotReplay() {
        AtomicInteger calls = new AtomicInteger();
        MongoException failure = abort();
        Thread.currentThread().interrupt();
        assertThatThrownBy(
                        () ->
                                MarkingWriteRetry.execute(
                                        () -> {
                                            calls.incrementAndGet();
                                            throw failure;
                                        }))
                .isSameAs(failure);
        assertThat(calls).hasValue(1);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }
}
