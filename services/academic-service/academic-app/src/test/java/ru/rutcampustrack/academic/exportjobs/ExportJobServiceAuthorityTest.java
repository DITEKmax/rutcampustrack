package ru.rutcampustrack.academic.exportjobs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.authsecurity.grpc.AuthSecurityCapabilityServiceGrpc;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    void reportAdmissionRpcDeadlineUsesOnlyRemainingRegistryBudget() {
        var observed = new AtomicReference<CallOptions>();
        var client = deadlineClient(observed);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100);
        // One millisecond allows only measurement/call-option construction overhead.
        Deadline ceiling = Deadline.after(101, TimeUnit.MILLISECONDS);
        Throwable failure =
                catchThrowable(() -> client.admit(identity, false, null, null, deadline));
        assertThat(failure).isInstanceOf(StatusRuntimeException.class);
        var code = ((StatusRuntimeException) failure).getStatus().getCode();
        if (observed.get() == null) {
            // Class initialization or a paused test JVM may exhaust this short budget first.
            assertThat(System.nanoTime()).isGreaterThanOrEqualTo(deadline);
            assertThat(code).isEqualTo(Status.Code.UNAVAILABLE);
        } else {
            assertThat(code).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
            assertThat(observed.get().getDeadline()).isNotNull();
            assertThat(observed.get().getDeadline().isBefore(ceiling)).isTrue();
        }
    }

    @Test
    void expiredReportBudgetDoesNotStartRpcWhileOrdinaryAdmissionKeepsThreeSecondCap() {
        var observed = new AtomicReference<CallOptions>();
        var client = deadlineClient(observed);
        assertThatThrownBy(() -> client.admit(identity, false, null, null, System.nanoTime() - 1))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.UNAVAILABLE));
        assertThat(observed.get()).isNull();
        assertThatThrownBy(() -> client.admit(identity, false, null, null))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.DEADLINE_EXCEEDED));
        assertThat(observed.get()).isNotNull();
        assertThat(observed.get().getDeadline()).isNotNull();
        assertThat(observed.get().getDeadline().timeRemaining(TimeUnit.NANOSECONDS))
                .isLessThanOrEqualTo(TimeUnit.SECONDS.toNanos(3));
    }

    private AuthReportSecurityClient deadlineClient(AtomicReference<CallOptions> observed) {
        var client =
                new AuthReportSecurityClient(
                        java.util.Base64.getUrlEncoder()
                                .withoutPadding()
                                .encodeToString(new byte[32]),
                        "auth-service");
        Channel channel =
                new Channel() {
                    @Override
                    public String authority() {
                        return "auth-service";
                    }

                    @Override
                    public <Q, S> ClientCall<Q, S> newCall(
                            MethodDescriptor<Q, S> method, CallOptions options) {
                        observed.set(options);
                        throw Status.DEADLINE_EXCEEDED.asRuntimeException();
                    }
                };
        org.springframework.test.util.ReflectionTestUtils.setField(
                client, "stub", AuthSecurityCapabilityServiceGrpc.newBlockingStub(channel));
        return client;
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
