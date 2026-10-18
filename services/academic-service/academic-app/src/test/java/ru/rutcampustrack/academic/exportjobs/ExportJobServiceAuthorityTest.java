package ru.rutcampustrack.academic.exportjobs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Instant;
import java.util.UUID;

class ExportJobServiceAuthorityTest {
    private final ExportJobRepository repository = mock(ExportJobRepository.class);
    private final LocalReportAuthority local = mock(LocalReportAuthority.class);
    private final ReportSecurityAuthority security = mock(ReportSecurityAuthority.class);
    private final ExportJobService service =
            new ExportJobService(repository, local, security, mock(Validator.class));
    private final InternalJwtClaims actor =
            new InternalJwtClaims(
                    7, UUID.randomUUID(), 1, 2, "ADMIN", "ACTIVE", null, false, false);
    private final IssueReportDownloadTicketRequest selector =
            new IssueReportDownloadTicketRequest(
                    ReportDownloadKind.ADMIN_USERS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new IssueReportDownloadTicketRequest.AdminUsersParameters(
                            null, null, null, null, null, ReportDownloadFormat.HTML),
                    null);
    private final StoredReportIdentity identity =
            new StoredReportIdentity(
                    actor, selector, Instant.now().plusSeconds(300), 3, null, null);

    private ExportJobRepository.Job ready(boolean full) {
        return new ExportJobRepository.Job(
                UUID.randomUUID(),
                UUID.randomUUID(),
                identity,
                "ready",
                1,
                UUID.randomUUID(),
                null,
                full,
                "text/html",
                null,
                Instant.now());
    }

    private ReportSecurityAuthority.Admission admitted(boolean full) {
        return new ReportSecurityAuthority.Admission(
                actor, "transient-only-token", Instant.now().plusSeconds(30), full);
    }

    @Test
    void currentAuthorityIsCheckedAgainAfterLoadingArtifactBytes() {
        var job = ready(false);
        when(repository.load(job.id())).thenReturn(job);
        when(security.admit(identity, false, null, null))
                .thenReturn(admitted(false))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        when(repository.content(job)).thenReturn(new byte[] {1});
        assertThatThrownBy(() -> service.file(actor, job.id()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository).content(job);
        verify(security, times(2)).admit(identity, false, null, null);
    }

    @Test
    void differentSessionAndChangedActorGenerationsNeverReadBytes() {
        var job = ready(false);
        when(repository.load(job.id())).thenReturn(job);
        var otherSession =
                new InternalJwtClaims(
                        7, UUID.randomUUID(), 1, 2, "ADMIN", "ACTIVE", null, false, false);
        var changed =
                new InternalJwtClaims(
                        7, actor.sessionId(), 2, 2, "ADMIN", "ACTIVE", null, false, false);
        assertThatThrownBy(() -> service.file(otherSession, job.id()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.file(changed, job.id()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never()).content(any());
        verifyNoInteractions(security);
    }

    @Test
    void fullPrivilegeBecomesStickyAtCompletionAndIsRequiredBeforePublishing() {
        var job =
                new ExportJobRepository.Job(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        identity,
                        "preparing",
                        1,
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(90),
                        false,
                        null,
                        null,
                        Instant.now());
        when(security.admit(identity, true, null, null))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        var file = new ExportJobService.GeneratedFile(new byte[] {1}, "text/html", 1, true, null);
        assertThatThrownBy(() -> service.complete(job, file))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(repository, never())
                .complete(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
    }

    @Test
    void authorityOutageCannotPublishOrDiscloseAnExistingArtifact() {
        var job = ready(true);
        when(repository.load(job.id())).thenReturn(job);
        when(security.admit(identity, true, null, null))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());
        assertThatThrownBy(() -> service.file(actor, job.id()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e ->
                                assertThat(e.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(repository, never()).content(any());
    }

    @Test
    void planCapabilityRevocationIsAnAuthorityDenialBeforeCallingProducer() {
        var job =
                new ExportJobRepository.Job(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        identity,
                        "preparing",
                        1,
                        UUID.randomUUID(),
                        Instant.now().plusSeconds(90),
                        false,
                        null,
                        null,
                        Instant.now());
        String digest = "a".repeat(64);
        when(security.admitPlan(identity, false, job.id(), job.leaseId(), digest))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        assertThatThrownBy(() -> service.renderAdmission(job, digest))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(local).require(identity, false);
        verifyNoInteractions(repository);
    }
}
