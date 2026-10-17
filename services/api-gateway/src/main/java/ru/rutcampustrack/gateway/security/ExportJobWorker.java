package ru.rutcampustrack.gateway.security;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import ru.rutcampustrack.auth.dto.ExportJobModels;

/** Bounded worker. Auth's durable lease, not process memory, owns retries and crash recovery. */
@Component
@ConditionalOnProperty(
        prefix = "rutcampustrack.export-worker",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExportJobWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportJobWorker.class);
    private static final Duration WORK_TIMEOUT = Duration.ofSeconds(60);
    private final InternalJwtIssuerClient auth;
    private final ReportDownloadTicketDownloadFilter reports;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile Disposable active;

    public ExportJobWorker(
            InternalJwtIssuerClient auth, ReportDownloadTicketDownloadFilter reports) {
        this.auth = auth;
        this.reports = reports;
    }

    @Scheduled(fixedDelayString = "${rutcampustrack.export-worker.poll-millis:1000}")
    public void tick() {
        if (!busy.compareAndSet(false, true)) return;
        active =
                runOne().doFinally(signal -> busy.set(false))
                        .subscribe(
                                ignored -> {},
                                failure ->
                                        log.warn(
                                                "Export worker dependency failed ({})",
                                                failure.getClass().getSimpleName()));
    }

    Mono<Void> runOne() {
        return auth.claimExport()
                .flatMap(claim -> claim.map(this::generate).orElseGet(Mono::empty));
    }

    private Mono<Void> generate(ExportJobModels.Claim claim) {
        log.info("Export worker stage=dispatch jobId={}", claim.jobId());
        return reports.generate(claim.authority(), claim.jobId(), claim.leaseId())
                .flatMap(
                        file ->
                                auth.completeExport(
                                                claim.jobId(),
                                                new ExportJobModels.Complete(
                                                        claim.leaseId(),
                                                        file.content(),
                                                        file.contentType(),
                                                        file.rowCount(),
                                                        file.requiresFull(),
                                                        file.teacherScopeHash()))
                                        .doOnSuccess(
                                                ignored ->
                                                        log.info(
                                                                "Export worker stage=commit_ack jobId={}",
                                                                claim.jobId())))
                // Include generation and completion; cancellation precedes Auth's 90-second lease.
                .timeout(WORK_TIMEOUT)
                .onErrorResume(
                        failure -> {
                            String code = ReportDownloadTicketDownloadFilter.failureCode(failure);
                            return auth.failExport(
                                            claim.jobId(),
                                            new ExportJobModels.Fail(claim.leaseId(), code))
                                    .doOnSuccess(
                                            ignored ->
                                                    log.info(
                                                            "Export worker stage=fail_ack jobId={} code={}",
                                                            claim.jobId(),
                                                            code))
                                    .doOnError(
                                            ignored ->
                                                    log.warn(
                                                            "Export worker stage=callback_failed jobId={} code={}",
                                                            claim.jobId(),
                                                            code));
                        })
                .doOnCancel(
                        () -> log.info("Export worker stage=cancelled jobId={}", claim.jobId()));
    }

    @PreDestroy
    public void stop() {
        Disposable current = active;
        if (current != null) current.dispose();
    }
}
