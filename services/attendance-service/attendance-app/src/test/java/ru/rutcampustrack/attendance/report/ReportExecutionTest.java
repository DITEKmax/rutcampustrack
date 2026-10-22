package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ReportExecutionTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ReportExecution execution = new ReportExecution(registry);

    @Test
    void originalOperationAutomaticallyRecoversWithBoundedBackoffAndExportsMeters() {
        AtomicInteger attempts = new AtomicInteger();
        long started = System.nanoTime();
        String result =
                execution.execute(
                        ReportExecution.Report.WEEKLY,
                        HeadmanWeeklyReportFormat.DOCX,
                        () -> {
                            if (attempts.incrementAndGet() < 3)
                                throw new AcademicServiceUnavailableException(
                                        "synthetic outage", io.grpc.Status.Code.UNAVAILABLE);
                            return "original-result";
                        });
        assertThat(result).isEqualTo("original-result");
        assertThat(attempts.get()).isEqualTo(3);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                .isGreaterThanOrEqualTo(290);
        assertThat(registry.get("attendance.report.retries.total").counter().count()).isEqualTo(2);
        assertThat(
                        registry.get("attendance.report.duration")
                                .tag("outcome", "success")
                                .timer()
                                .count())
                .isEqualTo(1);
        assertThat(registry.get("attendance.report.active").gauge().value()).isZero();
    }

    @Test
    void knownPermanentMetadataAndTransportFailuresAreAttemptedOnce() {
        for (RuntimeException permanent :
                java.util.List.of(
                        new AcademicServiceUnavailableException("invalid semester metadata"),
                        new ru.rutcampustrack.attendance.exception
                                .ScheduleServiceUnavailableException(
                                "invalid argument", io.grpc.Status.Code.INVALID_ARGUMENT),
                        new ru.rutcampustrack.attendance.exception
                                .ScheduleServiceUnavailableException(
                                "permission denied", io.grpc.Status.Code.PERMISSION_DENIED))) {
            AtomicInteger attempts = new AtomicInteger();
            assertThatThrownBy(
                            () ->
                                    execution.execute(
                                            ReportExecution.Report.WEEKLY,
                                            HeadmanWeeklyReportFormat.DOCX,
                                            () -> {
                                                attempts.incrementAndGet();
                                                throw permanent;
                                            }))
                    .isSameAs(permanent);
            assertThat(attempts.get()).isEqualTo(1);
        }
        assertThat(registry.find("attendance.report.retries.total").counter()).isNull();
    }

    @Test
    void unauthenticatedAndInterruptedOperationsAreExcludedWithoutLeakingExecutionSlots() {
        assertThatThrownBy(
                        () ->
                                execution.execute(
                                        ReportExecution.Report.WEEKLY,
                                        HeadmanWeeklyReportFormat.DOCX,
                                        () -> {
                                            throw new org.springframework.web.server
                                                    .ResponseStatusException(
                                                    org.springframework.http.HttpStatus
                                                            .UNAUTHORIZED);
                                        }))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(
                        () ->
                                execution.execute(
                                        ReportExecution.Report.WEEKLY,
                                        HeadmanWeeklyReportFormat.DOCX,
                                        () -> {
                                            throw new ru.rutcampustrack.attendance.contract
                                                    .exception.ResourceNotFoundException(
                                                    "Semester", "status", "active");
                                        }))
                .isInstanceOf(
                        ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException
                                .class);
        for (boolean beforeOperation : java.util.List.of(true, false)) {
            AtomicInteger attempts = new AtomicInteger();
            if (beforeOperation) Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(
                                () ->
                                        execution.execute(
                                                ReportExecution.Report.WEEKLY,
                                                HeadmanWeeklyReportFormat.DOCX,
                                                () -> {
                                                    attempts.incrementAndGet();
                                                    Thread.currentThread().interrupt();
                                                    throw new AcademicServiceUnavailableException(
                                                            "synthetic outage",
                                                            io.grpc.Status.Code.UNAVAILABLE);
                                                }))
                        .isInstanceOf(ReportExportUnavailableException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally {
                Thread.interrupted();
            }
            assertThat(attempts.get()).isEqualTo(beforeOperation ? 0 : 1);
        }
        assertThat(
                        registry.get("attendance.report.last.failure.timestamp.seconds")
                                .tags("report", "weekly", "format", "docx")
                                .gauge()
                                .value())
                .isZero();
        assertThat(registry.get("attendance.report.active").gauge().value()).isZero();
        assertThat(
                        registry.get("attendance.report.duration")
                                .tag("outcome", "rejected")
                                .timer()
                                .count())
                .isEqualTo(4);
        assertThat(
                        execution.execute(
                                ReportExecution.Report.WEEKLY,
                                HeadmanWeeklyReportFormat.DOCX,
                                () -> "next"))
                .isEqualTo("next");
    }

    @Test
    void exhaustedTransientIsExplicitTerminalAndInvalidInputIsNeverRetried() {
        assertThat(
                        registry.get("attendance.report.last.failure.timestamp.seconds")
                                .tags("report", "weekly", "format", "docx")
                                .gauge()
                                .value())
                .isZero();
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                execution.execute(
                                        ReportExecution.Report.WEEKLY,
                                        HeadmanWeeklyReportFormat.DOCX,
                                        () -> {
                                            attempts.incrementAndGet();
                                            throw new AcademicServiceUnavailableException(
                                                    "synthetic outage",
                                                    io.grpc.Status.Code.UNAVAILABLE);
                                        }))
                .isInstanceOf(ReportExportUnavailableException.class);
        assertThat(attempts.get()).isEqualTo(3);
        AtomicInteger invalidAttempts = new AtomicInteger();
        assertThatThrownBy(
                        () ->
                                execution.execute(
                                        ReportExecution.Report.WEEKLY,
                                        HeadmanWeeklyReportFormat.DOCX,
                                        () -> {
                                            invalidAttempts.incrementAndGet();
                                            throw new ReportValidationException(
                                                    "synthetic invalid selection");
                                        }))
                .isInstanceOf(ReportValidationException.class);
        assertThat(invalidAttempts.get()).isEqualTo(1);
        assertThat(
                        registry.get("attendance.report.last.failure.timestamp.seconds")
                                .tags("report", "weekly", "format", "docx")
                                .gauge()
                                .value())
                .isGreaterThan(0);
        assertThat(
                        registry.get("attendance.report.duration")
                                .tag("outcome", "failure")
                                .timer()
                                .count())
                .isEqualTo(1);
        assertThat(
                        registry.get("attendance.report.duration")
                                .tag("outcome", "rejected")
                                .timer()
                                .count())
                .isEqualTo(1);
        assertThat(
                        execution.execute(
                                ReportExecution.Report.WEEKLY,
                                HeadmanWeeklyReportFormat.DOCX,
                                () -> "next"))
                .isEqualTo("next");
    }

    @Test
    void concurrencyIsTwoBusyFailsPromptlyAndInterruptedWorkerReleasesItsSlot() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> operation =
                    () ->
                            execution.execute(
                                    ReportExecution.Report.WEEKLY,
                                    HeadmanWeeklyReportFormat.DOCX,
                                    () -> {
                                        entered.countDown();
                                        try {
                                            release.await();
                                        } catch (InterruptedException interrupted) {
                                            Thread.currentThread().interrupt();
                                            throw new AcademicServiceUnavailableException(
                                                    "synthetic interruption",
                                                    io.grpc.Status.Code.UNAVAILABLE);
                                        }
                                        return "done";
                                    });
            var first = workers.submit(operation);
            var second = workers.submit(operation);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(registry.get("attendance.report.active").gauge().value()).isEqualTo(2);
            assertThatThrownBy(
                            () ->
                                    execution.execute(
                                            ReportExecution.Report.WEEKLY,
                                            HeadmanWeeklyReportFormat.DOCX,
                                            () -> "third"))
                    .isInstanceOf(ReportExportUnavailableException.class);
            assertThat(
                            registry.get("attendance.report.duration")
                                    .tag("outcome", "busy")
                                    .timer()
                                    .count())
                    .isEqualTo(1);
            assertThat(
                            execution.observe(
                                    ReportExecution.Report.WEEKLY,
                                    HeadmanWeeklyReportFormat.DOCX,
                                    () -> {
                                        assertThat(
                                                        registry.get("attendance.report.active")
                                                                .gauge()
                                                                .value())
                                                .isEqualTo(2);
                                        return "planned";
                                    }))
                    .isEqualTo("planned");
            assertThat(
                            registry.get("attendance.report.duration")
                                    .tags(
                                            "report", "weekly", "format", "docx", "outcome",
                                            "success")
                                    .timer()
                                    .count())
                    .isEqualTo(1);
            first.cancel(true);
            release.countDown();
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            release.countDown();
        }
        assertThat(registry.get("attendance.report.active").gauge().value()).isZero();
        assertThat(
                        execution.execute(
                                ReportExecution.Report.WEEKLY,
                                HeadmanWeeklyReportFormat.DOCX,
                                () -> "next"))
                .isEqualTo("next");
    }
}
