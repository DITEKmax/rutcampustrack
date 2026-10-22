package ru.rutcampustrack.academic.exportjobs;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** One Academic queue admits at most two live leases, regardless of process concurrency. */
@Component
@ConditionalOnProperty(
        prefix = "rutcampustrack.export-worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExportJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportJobWorker.class);
    private final ExportJobService jobs;
    private final ReportJobRenderer renderer;
    private final AtomicInteger active = new AtomicInteger();
    private final ExecutorService workers =
            Executors.newFixedThreadPool(
                    2,
                    r -> {
                        var thread = new Thread(r, "academic-report-worker");
                        thread.setDaemon(true);
                        return thread;
                    });

    public ExportJobWorker(ExportJobService jobs, ReportJobRenderer renderer) {
        this.jobs = jobs;
        this.renderer = renderer;
    }

    @Scheduled(fixedDelayString = "${rutcampustrack.export-worker.poll-millis:1000}")
    @SingleInstanceScheduled(
            reason =
                    "Single worker uses a bounded two-thread dispatcher; existing DB claim locking"
                        + " and live-lease CAS enforce the global two-job limit.")
    public void tick() {
        for (int slot = 0; slot < 2; slot++) {
            if (active.get() >= 2) return;
            int before = active.get();
            if (!active.compareAndSet(before, before + 1)) continue;
            try {
                workers.submit(
                        () -> {
                            try {
                                runOne();
                            } catch (RuntimeException failure) {
                                log.warn(
                                        "Academic export dependency failed ({})",
                                        failure.getClass().getSimpleName());
                            } finally {
                                active.decrementAndGet();
                            }
                        });
            } catch (RejectedExecutionException stopped) {
                active.decrementAndGet();
                return;
            }
        }
    }

    void runOne() {
        var claimed = jobs.claim();
        if (claimed.isEmpty()) return;
        var job = claimed.get();
        log.info("Export worker stage=dispatch jobId={} attempt={}", job.id(), job.attempts());
        try {
            var file = renderer.render(job);
            jobs.complete(job, file);
            log.info("Export worker stage=commit_ack jobId={}", job.id());
        } catch (RuntimeException failure) {
            String code = code(failure);
            try {
                jobs.fail(job, code);
            } catch (RuntimeException callback) {
                log.warn("Export worker stage=callback_failed jobId={} code={}", job.id(), code);
            }
        }
    }

    private static String code(RuntimeException failure) {
        if (failure instanceof ResponseStatusException response) {
            int status = response.getStatusCode().value();
            if (status == 401 || status == 403) return "AUTHORITY_DENIED";
            if (status == 413) return "EXPORT_TOO_LARGE";
        }
        return "GENERATION_FAILED";
    }

    @PreDestroy
    public void stop() {
        workers.shutdownNow();
    }
}
