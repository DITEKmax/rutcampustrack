package ru.rutcampustrack.gateway.security;

import jakarta.annotation.PreDestroy;
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
                                                file.requiresFull())))
                .onErrorResume(
                        failure ->
                                auth.failExport(
                                        claim.jobId(),
                                        new ExportJobModels.Fail(
                                                claim.leaseId(),
                                                ReportDownloadTicketDownloadFilter.failureCode(
                                                        failure))));
    }

    @PreDestroy
    public void stop() {
        Disposable current = active;
        if (current != null) current.dispose();
    }
}
