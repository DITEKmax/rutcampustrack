package ru.rutcampustrack.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.rutcampustrack.auth.dto.ExportJobModels;

class ExportJobWorkerTest {
    @Test
    void readyCommitReceivesBytesAndPrivilegeFlagOnlyAfterGenerationCompletes() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId()))
                .thenReturn(
                        Mono.just(
                                new ReportDownloadTicketDownloadFilter.GeneratedFile(
                                        new byte[] {1}, "text/html", 3, true, "a".repeat(64))));
        when(auth.completeExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());
        StepVerifier.create(new ExportJobWorker(auth, reports).runOne()).verifyComplete();
        verify(auth)
                .completeExport(
                        eq(claim.jobId()),
                        argThat(
                                result ->
                                        result.leaseId().equals(claim.leaseId())
                                                && result.rowCount() == 3
                                                && result.requiresFull()
                                                && result.content().length == 1
                                                && "a"
                                                        .repeat(64)
                                                        .equals(result.teacherScopeHash())));
        verify(auth, never()).failExport(any(), any());
    }

    @Test
    void dependencyFailureReportsOnlySafeRetryCodeAndNeverCommitsPartialBytes() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId()))
                .thenReturn(Mono.error(new IllegalStateException("synthetic private detail")));
        when(auth.failExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());
        StepVerifier.create(new ExportJobWorker(auth, reports).runOne()).verifyComplete();
        verify(auth, never()).completeExport(any(), any());
        verify(auth)
                .failExport(
                        claim.jobId(),
                        new ExportJobModels.Fail(claim.leaseId(), "GENERATION_FAILED"));
    }

    @Test
    void anActiveClaimPreventsDuplicateScheduledWorkAndShutdownCancelsIt() {
        var auth = mock(InternalJwtIssuerClient.class);
        when(auth.claimExport()).thenReturn(Mono.never());
        var worker = new ExportJobWorker(auth, mock(ReportDownloadTicketDownloadFilter.class));
        worker.tick();
        worker.tick();
        verify(auth, times(1)).claimExport();
        worker.stop();
    }

    @Test
    void stalledGenerationIsCancelledAndDurablyRequeuedBeforeTheLeaseExpires() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        var cancelled = new AtomicBoolean();
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId()))
                .thenReturn(
                        Mono.<ReportDownloadTicketDownloadFilter.GeneratedFile>never()
                                .doOnCancel(() -> cancelled.set(true)));
        when(auth.failExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> new ExportJobWorker(auth, reports).runOne())
                .thenAwait(Duration.ofSeconds(60))
                .expectComplete()
                .verify(Duration.ofSeconds(2));

        assertThat(cancelled).isTrue();
        verify(auth, never()).completeExport(any(), any());
        verify(auth)
                .failExport(
                        claim.jobId(),
                        new ExportJobModels.Fail(claim.leaseId(), "GENERATION_FAILED"));
    }

    @Test
    void completionIsBoundedAsWellAsGeneration() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        var cancelled = new AtomicBoolean();
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId()))
                .thenReturn(
                        Mono.just(
                                new ReportDownloadTicketDownloadFilter.GeneratedFile(
                                        new byte[] {1}, "text/html", 1, false, null)));
        when(auth.completeExport(eq(claim.jobId()), any()))
                .thenReturn(Mono.<Void>never().doOnCancel(() -> cancelled.set(true)));
        when(auth.failExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());

        StepVerifier.withVirtualTime(() -> new ExportJobWorker(auth, reports).runOne())
                .thenAwait(Duration.ofSeconds(60))
                .expectComplete()
                .verify(Duration.ofSeconds(2));

        assertThat(cancelled).isTrue();
        verify(auth)
                .failExport(
                        claim.jobId(),
                        new ExportJobModels.Fail(claim.leaseId(), "GENERATION_FAILED"));
    }
}
