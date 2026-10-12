package ru.rutcampustrack.gateway.security;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.rutcampustrack.auth.dto.ExportJobModels;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ExportJobWorkerTest {
    @Test void readyCommitReceivesBytesAndPrivilegeFlagOnlyAfterGenerationCompletes() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId())).thenReturn(Mono.just(new ReportDownloadTicketDownloadFilter.GeneratedFile(new byte[]{1}, "text/html", 3, true)));
        when(auth.completeExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());
        StepVerifier.create(new ExportJobWorker(auth, reports).runOne()).verifyComplete();
        verify(auth).completeExport(eq(claim.jobId()), argThat(result -> result.leaseId().equals(claim.leaseId()) && result.rowCount() == 3 && result.requiresFull() && result.content().length == 1));
        verify(auth, never()).failExport(any(), any());
    }
    @Test void dependencyFailureReportsOnlySafeRetryCodeAndNeverCommitsPartialBytes() {
        var auth = mock(InternalJwtIssuerClient.class);
        var reports = mock(ReportDownloadTicketDownloadFilter.class);
        var claim = new ExportJobModels.Claim(UUID.randomUUID(), UUID.randomUUID(), null);
        when(auth.claimExport()).thenReturn(Mono.just(Optional.of(claim)));
        when(reports.generate(null, claim.jobId(), claim.leaseId())).thenReturn(Mono.error(new IllegalStateException("synthetic private detail")));
        when(auth.failExport(eq(claim.jobId()), any())).thenReturn(Mono.empty());
        StepVerifier.create(new ExportJobWorker(auth, reports).runOne()).verifyComplete();
        verify(auth, never()).completeExport(any(), any());
        verify(auth).failExport(claim.jobId(), new ExportJobModels.Fail(claim.leaseId(), "GENERATION_FAILED"));
    }
    @Test void anActiveClaimPreventsDuplicateScheduledWorkAndShutdownCancelsIt() {
        var auth = mock(InternalJwtIssuerClient.class);
        when(auth.claimExport()).thenReturn(Mono.never());
        var worker = new ExportJobWorker(auth, mock(ReportDownloadTicketDownloadFilter.class));
        worker.tick(); worker.tick();
        verify(auth, times(1)).claimExport();
        worker.stop();
    }
}
