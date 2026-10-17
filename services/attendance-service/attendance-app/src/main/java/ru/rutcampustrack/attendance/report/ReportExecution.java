package ru.rutcampustrack.attendance.report;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/** Bounded recovery of the original synchronous read/export; no background job is implied. */
@Component
public final class ReportExecution {
    private static final class ExportCancelled extends ReportExportUnavailableException {
        private ExportCancelled() {
            super("Формирование отчёта прервано.");
        }
    }

    private static final int MAX_ATTEMPTS = 3;
    private final Semaphore permits = new Semaphore(2);
    private final MeterRegistry registry;

    private record Key(Report report, HeadmanWeeklyReportFormat format) {}

    private final java.util.Map<Key, java.util.concurrent.atomic.AtomicLong> failures =
            new java.util.HashMap<>();

    public enum Report {
        WEEKLY("weekly"),
        SUBJECT_JOURNAL("subject_journal");

        private final String code;

        Report(String code) {
            this.code = code;
        }
    }

    public ReportExecution(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("attendance.report.active", permits, value -> 2 - value.availablePermits())
                .description("Synchronous attendance exports holding one of two execution slots")
                .register(registry);
        for (Report report : Report.values()) {
            for (HeadmanWeeklyReportFormat format : HeadmanWeeklyReportFormat.values()) {
                var timestamp = new java.util.concurrent.atomic.AtomicLong();
                failures.put(new Key(report, format), timestamp);
                Gauge.builder(
                                "attendance.report.last.failure.timestamp.seconds",
                                timestamp,
                                java.util.concurrent.atomic.AtomicLong::get)
                        .description(
                                "Real UTC epoch seconds of the most recent terminal export failure"
                                        + " or busy rejection")
                        .tags("report", report.code, "format", format.code())
                        .register(registry);
            }
        }
    }

    public <T> T execute(Report report, HeadmanWeeklyReportFormat format, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(registry);
        if (!permits.tryAcquire()) {
            finish(sample, report, format, "busy");
            throw new ReportExportUnavailableException(
                    "Сервер формирования занят. Повтори запрос позже.");
        }
        String outcome = "failure";
        try {
            for (int attempt = 1; ; attempt++) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new ExportCancelled();
                }
                try {
                    T result = operation.get();
                    outcome = "success";
                    return result;
                } catch (RuntimeException failure) {
                    if (!transientDependency(failure)) throw failure;
                    if (attempt >= MAX_ATTEMPTS) {
                        throw new ReportExportUnavailableException(
                                "Не удалось сформировать отчёт после ограниченного восстановления."
                                        + " Повтори запрос позже.");
                    }
                    try {
                        Thread.sleep(100L << (attempt - 1));
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ExportCancelled();
                    }
                    Counter.builder("attendance.report.retries.total")
                            .tags(
                                    "report",
                                    report.code,
                                    "format",
                                    format.name().toLowerCase(java.util.Locale.ROOT))
                            .register(registry)
                            .increment();
                }
            }
        } catch (RuntimeException failure) {
            if (failure instanceof ru.rutcampustrack.attendance.exception.ReportValidationException
                    || failure
                            instanceof
                            ru.rutcampustrack.attendance.exception.ReportExportTooLargeException
                    || failure
                            instanceof ru.rutcampustrack.attendance.exception.AccessDeniedException
                    || failure instanceof ru.rutcampustrack.attendance.exception.BadRequestException
                    || failure
                            instanceof
                            ru.rutcampustrack.attendance.contract.exception
                                    .ResourceNotFoundException
                    || failure instanceof ru.rutcampustrack.attendance.exception.ConflictException
                    || failure instanceof ru.rutcampustrack.attendance.exception.RateLimitException
                    || failure instanceof org.springframework.security.access.AccessDeniedException
                    || failure instanceof java.util.concurrent.CancellationException
                    || failure instanceof ExportCancelled
                    || (failure
                                    instanceof
                                    org.springframework.web.server.ResponseStatusException status
                            && status.getStatusCode().is4xxClientError())) {
                outcome = "rejected";
            }
            throw failure;
        } finally {
            permits.release();
            finish(sample, report, format, outcome);
        }
    }

    private static boolean transientDependency(RuntimeException failure) {
        // Do not traverse arbitrary cause chains: an authorization/domain/render failure may
        // contain a transient cause without making the complete operation safe to retry.
        return (failure instanceof AcademicServiceUnavailableException academic
                        && academic.isRetryable())
                || (failure instanceof ScheduleServiceUnavailableException schedule
                        && schedule.isRetryable())
                || failure instanceof TransientDataAccessException;
    }

    private void finish(
            Timer.Sample sample, Report report, HeadmanWeeklyReportFormat format, String outcome) {
        if ("failure".equals(outcome) || "busy".equals(outcome)) {
            failures.get(new Key(report, format)).set(java.time.Instant.now().getEpochSecond());
        }
        sample.stop(
                Timer.builder("attendance.report.duration")
                        .description("Original synchronous export including bounded recovery")
                        .tags(
                                "report",
                                report.code,
                                "format",
                                format.name().toLowerCase(java.util.Locale.ROOT),
                                "outcome",
                                outcome)
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(java.time.Duration.ofSeconds(30))
                        .register(registry));
    }
}
