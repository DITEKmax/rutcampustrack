package ru.rutcampustrack.academic.exportjobs;

import io.grpc.StatusRuntimeException;
import jakarta.validation.Validator;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.academic.contract.dto.export.RegistryExportModels;
import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.Create;
import ru.rutcampustrack.academic.scheduling.SingleInstanceScheduled;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.authauthority.grpc.AuthorityAdminProfile;
import ru.rutcampustrack.authauthority.grpc.CheckAdminAccessResponse;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Owner orchestration; every remote check runs outside the repository's short transactions. */
@Service
public class ExportJobService {
    private final ExportJobRepository jobs;
    private final LocalReportAuthority local;
    private final ReportSecurityAuthority security;
    private final Validator validator;

    public ExportJobService(
            ExportJobRepository jobs,
            LocalReportAuthority local,
            ReportSecurityAuthority security,
            Validator validator) {
        this.jobs = jobs;
        this.local = local;
        this.security = security;
        this.validator = validator;
    }

    public ExportJobRepository.Job enqueue(InternalJwtClaims actor, Create request) {
        if (request == null
                || request.requestId() == null
                || request.report() == null
                || !validator.validate(request.report()).isEmpty())
            throw ExportJobRepository.error(HttpStatus.BAD_REQUEST);
        var identity = local.plan(actor, request.report(), Instant.now().plusSeconds(600));
        var admission = admit(identity, false, null, null);
        return jobs.enqueue(
                request.requestId(),
                identity,
                request.report().kind() == ReportDownloadKind.ADMIN_USERS && admission.full());
    }

    public ExportJobRepository.Job status(InternalJwtClaims actor, UUID id) {
        var job = owned(actor, id);
        authorize(job);
        return job;
    }

    /** A transient read scope for the exact admitted ADMIN_USERS render; it contains no bearer. */
    public UserRegistryReadScope userRegistryReadScope(ExportJobRepository.Job job) {
        if (job == null
                || job.leaseId() == null
                || job.identity().report().kind() != ReportDownloadKind.ADMIN_USERS)
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        ExportJobRepository.requireLease(job, job.leaseId());
        return new UserRegistryReadScope(job);
    }

    public final class UserRegistryReadScope {
        private final ExportJobRepository.Job expected;

        private UserRegistryReadScope(ExportJobRepository.Job expected) {
            this.expected = expected;
        }

        public void requireBinding(UUID job, UUID lease, InternalJwtClaims actor) {
            if (!expected.id().equals(job)
                    || !expected.leaseId().equals(lease)
                    || !expected.identity().actor().equals(actor))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        }

        public void requireSelector(RegistryExportModels.Users query) {
            var selected = expected.identity().report().adminUsers();
            var sorts =
                    selected.sorts() == null
                            ? null
                            : selected.sorts().stream()
                                    .map(
                                            order ->
                                                    new RegistryExportModels.SortOrder(
                                                            order.field(), order.descending()))
                                    .toList();
            if (query == null
                    || !Objects.equals(selected.search(), query.search())
                    || !Objects.equals(selected.role(), query.role())
                    || !Objects.equals(
                            selected.status(),
                            query.status() == null ? null : query.status().name())
                    || !Objects.equals(selected.roleStatus(), query.roleStatus())
                    || !Objects.equals(selected.groupId(), query.groupId())
                    || !Objects.equals(sorts, query.sorts())
                    || !selected.format().code().equals(query.format()))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        }

        public CheckAdminAccessResponse check(long deadlineNanos) {
            requireDeadline(deadlineNanos);
            var current = currentJob();
            local.require(current.identity(), false);
            requireDeadline(deadlineNanos);
            // Auth re-admits the stored session, USERS_VIEW and sticky FULL. A read needs no
            // new report bearer, and the existing per-RPC bound remains three seconds.
            var admitted =
                    admit(
                            current.identity(),
                            expected.requiresFull() || current.requiresFull(),
                            null,
                            null,
                            deadlineNanos);
            requireDeadline(deadlineNanos);
            currentJob();
            var profile = admitted.adminProfile();
            if (profile == null
                    || !profile.assigned()
                    || profile.userId() != admitted.actor().userId()
                    || (!profile.full() && !profile.allows("USERS_VIEW"))
                    || profile.full() != admitted.full())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return CheckAdminAccessResponse.newBuilder()
                    .setActorUserId(admitted.actor().userId())
                    .setSessionVersion(admitted.actor().sessionVersion())
                    .setRolesVersion(admitted.actor().rolesVersion())
                    .setProfile(
                            AuthorityAdminProfile.newBuilder()
                                    .setAssigned(profile.assigned())
                                    .setMode(profile.mode())
                                    .setVersion(profile.version())
                                    .addAllPermissions(profile.permissions()))
                    .build();
        }

        private ExportJobRepository.Job currentJob() {
            var current = owned(expected.identity().actor(), expected.id());
            ExportJobRepository.requireLease(current, expected.leaseId());
            if (!expected.identity().equals(current.identity()))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return current;
        }

        private void requireDeadline(long deadlineNanos) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()
                    || System.nanoTime() >= deadlineNanos)
                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** Exact transient ADMIN_GROUP_ATTENDANCE read authority; no bearer is retained. */
    public GroupAttendanceReadScope groupAttendanceReadScope(ExportJobRepository.Job job) {
        if (job == null
                || job.leaseId() == null
                || job.identity().report().kind() != ReportDownloadKind.ADMIN_GROUP_ATTENDANCE)
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        ExportJobRepository.requireLease(job, job.leaseId());
        return new GroupAttendanceReadScope(job);
    }

    public final class GroupAttendanceReadScope {
        private final ExportJobRepository.Job expected;
        private final long deadlineNanos;

        private GroupAttendanceReadScope(ExportJobRepository.Job expected) {
            this.expected = expected;
            long remaining =
                    Duration.between(Instant.now(), expected.leaseUntil().minusSeconds(30))
                            .toNanos();
            if (remaining <= 0) throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
            deadlineNanos = System.nanoTime() + remaining;
        }

        public void requireBinding(
                UUID job,
                UUID lease,
                InternalJwtClaims actor,
                ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels
                                .Export
                        query) {
            if (!expected.id().equals(job)
                    || !expected.leaseId().equals(lease)
                    || !expected.identity().actor().equals(actor))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            requireSelector(query);
        }

        public void requireSelector(
                ru.rutcampustrack.academic.contract.dto.adminattendance.AdminAttendanceReportModels
                                .Export
                        query) {
            var selected = expected.identity().report().adminGroupAttendance();
            if (query == null
                    || query.sort() == null
                    || query.direction() == null
                    || query.format() == null
                    || !selected.semesterId().equals(query.semesterId())
                    || !selected.search().equals(query.search())
                    || !selected.sort().equals(query.sort().name())
                    || !selected.direction().equals(query.direction().name())
                    || !selected.format().code().equals(query.format().name()))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        }

        public ExportJobRepository.Job job() {
            requireDeadline();
            var current = owned(expected.identity().actor(), expected.id());
            ExportJobRepository.requireLease(current, expected.leaseId());
            if (!expected.identity().equals(current.identity()))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return current;
        }

        public long remainingNanos() {
            requireDeadline();
            return deadlineNanos - System.nanoTime();
        }

        public void check() {
            var current = job();
            local.require(current.identity(), false);
            requireProfile(admit(current.identity(), false, null, null, deadlineNanos));
            job();
        }

        public ReportSecurityAuthority.Admission admitPlan(
                ru.rutcampustrack.reportrendering.grpc.ReportRenderPlan plan) {
            check();
            var current = job();
            var actor = current.identity().actor();
            var wire = plan.getActor();
            if (!plan.hasAdminGroupStats()
                    || plan.getAdminGroupStats().getSemesterId() != current.identity().semesterId()
                    || !plan.getReportKind()
                            .equals(ReportDownloadKind.ADMIN_GROUP_ATTENDANCE.code())
                    || !plan.getJobId().equals(current.id().toString())
                    || !plan.getLeaseId().equals(current.leaseId().toString())
                    || wire.getUserId() != actor.userId()
                    || !wire.getSessionId().equals(actor.sessionId().toString())
                    || wire.getSessionVersion() != actor.sessionVersion()
                    || wire.getRolesVersion() != actor.rolesVersion()
                    || !wire.getRole().equals(actor.role())
                    || !wire.getStatus().equals(actor.status())
                    || wire.hasGroupId() != (actor.groupId() != null)
                    || wire.hasGroupId() && !Objects.equals(wire.getGroupId(), actor.groupId())
                    || wire.getIsHeadman() != actor.isHeadman()
                    || wire.getReadOnly() != actor.readOnly())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            String hash;
            try {
                hash =
                        HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(plan.toByteArray()));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
            try {
                var admitted =
                        security.admitPlan(
                                current.identity(),
                                false,
                                current.id(),
                                current.leaseId(),
                                hash,
                                deadlineNanos);
                requireProfile(admitted);
                job();
                return admitted;
            } catch (StatusRuntimeException failure) {
                throw map(failure);
            }
        }

        private void requireProfile(ReportSecurityAuthority.Admission admitted) {
            var p = admitted.adminProfile();
            if (!expected.identity().actor().equals(admitted.actor())
                    || p == null
                    || !p.assigned()
                    || p.userId() != admitted.actor().userId()
                    || p.full() != admitted.full()
                    || !p.full() && !p.allows("DASHBOARD_VIEW"))
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            requireDeadline();
        }

        private void requireDeadline() {
            if (org.springframework.transaction.support.TransactionSynchronizationManager
                            .isActualTransactionActive()
                    || System.nanoTime() >= deadlineNanos)
                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public Download file(InternalJwtClaims actor, UUID id) {
        var job = owned(actor, id);
        authorize(job);
        if (!"ready".equals(job.state())) throw ExportJobRepository.error(HttpStatus.CONFLICT);
        byte[] content = jobs.content(job);
        authorize(job);
        return new Download(
                job.identity().report().suggestedFilename(), job.contentType(), content);
    }

    public ReportDownloadTicketResponse ticket(InternalJwtClaims actor, UUID id) {
        var job = status(actor, id);
        if (!"ready".equals(job.state())) throw ExportJobRepository.error(HttpStatus.CONFLICT);
        return issue(job);
    }

    /** Compatibility creates work in the very same queue; the download waits for that work. */
    public ReportDownloadTicketResponse legacyTicket(
            InternalJwtClaims actor, IssueReportDownloadTicketRequest report) {
        return issue(enqueue(actor, new Create(UUID.randomUUID(), report)));
    }

    public Download downloadTicket(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9_-]{43}"))
            throw ExportJobRepository.error(HttpStatus.NOT_FOUND);
        final ReportSecurityAuthority.Redemption redeemed;
        try {
            redeemed = security.redeem(ticket);
        } catch (StatusRuntimeException failure) {
            throw map(failure);
        }
        var job = owned(redeemed.admission().actor(), redeemed.jobId());
        if (!job.identity().report().kind().code().equals(redeemed.kind())
                || !job.identity().report().bindingHash().equals(redeemed.hash()))
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        long deadline = System.nanoTime() + Duration.ofSeconds(55).toNanos();
        while (!Set.of("ready", "error").contains(job.state()) && System.nanoTime() < deadline) {
            authorize(job);
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
            }
            job = jobs.load(job.id());
        }
        if (!"ready".equals(job.state()))
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        return file(redeemed.admission().actor(), job.id());
    }

    public Optional<ExportJobRepository.Job> claim() {
        jobs.recoverAbandoned();
        var candidate = jobs.candidate();
        if (candidate.isEmpty()) return Optional.empty();
        var job = candidate.get();
        try {
            var admission = authorize(job);
            return jobs.claim(
                    job,
                    job.requiresFull()
                            || job.identity().report().kind() == ReportDownloadKind.ADMIN_USERS
                                    && admission.full());
        } catch (ResponseStatusException failure) {
            if (failure.getStatusCode().value() != 403) throw failure;
            jobs.deny(job);
            return Optional.empty();
        }
    }

    public ReportSecurityAuthority.Admission renderAdmission(ExportJobRepository.Job job) {
        ExportJobRepository.requireLease(job, job.leaseId());
        local.require(job.identity(), false);
        return admit(job.identity(), job.requiresFull(), job.id(), job.leaseId());
    }

    public ReportSecurityAuthority.Admission renderAdmission(
            ExportJobRepository.Job job, String planDigest) {
        ExportJobRepository.requireLease(job, job.leaseId());
        local.require(job.identity(), false);
        try {
            var admitted =
                    security.admitPlan(
                            job.identity(),
                            job.requiresFull(),
                            job.id(),
                            job.leaseId(),
                            planDigest);
            if (!job.identity().actor().equals(admitted.actor())
                    || job.requiresFull() && !admitted.full())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return admitted;
        } catch (StatusRuntimeException failure) {
            throw map(failure);
        }
    }

    public ExportJobRepository.Job renderPlan(UUID id, UUID lease, InternalJwtClaims actor) {
        var job = owned(actor, id);
        ExportJobRepository.requireLease(job, lease);
        authorize(job);
        return job;
    }

    public void complete(ExportJobRepository.Job job, GeneratedFile file) {
        admit(job.identity(), job.requiresFull() || file.requiresFull(), null, null);
        local.require(job.identity(), false);
        var captured = local.capture(job.identity(), file.teacherScopeHash());
        // Local scope is checked again in the final CAS transaction, after remote admission.
        jobs.complete(
                job.id(),
                job.leaseId(),
                file.content(),
                file.contentType(),
                file.rowCount(),
                file.requiresFull(),
                captured);
    }

    public void fail(ExportJobRepository.Job job, String code) {
        jobs.fail(job.id(), job.leaseId(), code);
    }

    @Scheduled(fixedDelayString = "${rutcampustrack.export-jobs.cleanup-millis:60000}")
    @SingleInstanceScheduled(
            reason =
                    "Single worker cleans bounded batches; existing row locks and SKIP LOCKED"
                            + " isolate terminal transitions and DELETE RETURNING expiry effects.")
    public void cleanExpired() {
        jobs.cleanExpired();
    }

    private ReportDownloadTicketResponse issue(ExportJobRepository.Job job) {
        authorize(job);
        try {
            var cap = security.issue(job.identity(), job.requiresFull(), job.id());
            return new ReportDownloadTicketResponse(
                    "/api/report-download/" + cap.ticket(),
                    cap.expiresAt(),
                    job.identity().report().suggestedFilename());
        } catch (StatusRuntimeException failure) {
            throw map(failure);
        }
    }

    private ExportJobRepository.Job owned(InternalJwtClaims actor, UUID id) {
        var job = jobs.load(id);
        if (actor == null
                || actor.userId() != job.identity().actor().userId()
                || !actor.sessionId().equals(job.identity().actor().sessionId()))
            throw ExportJobRepository.error(HttpStatus.NOT_FOUND);
        if (!actor.equals(job.identity().actor()))
            throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
        return job;
    }

    private ReportSecurityAuthority.Admission authorize(ExportJobRepository.Job job) {
        local.require(job.identity(), "ready".equals(job.state()));
        var admitted = admit(job.identity(), job.requiresFull(), null, null);
        local.require(job.identity(), "ready".equals(job.state()));
        return admitted;
    }

    private ReportSecurityAuthority.Admission admit(
            StoredReportIdentity identity, boolean full, UUID job, UUID lease) {
        try {
            var admitted = security.admit(identity, full, job, lease);
            if (!identity.actor().equals(admitted.actor()) || full && !admitted.full())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return admitted;
        } catch (StatusRuntimeException failure) {
            throw map(failure);
        }
    }

    private ReportSecurityAuthority.Admission admit(
            StoredReportIdentity identity, boolean full, UUID job, UUID lease, long deadlineNanos) {
        try {
            var admitted = security.admit(identity, full, job, lease, deadlineNanos);
            if (!identity.actor().equals(admitted.actor()) || full && !admitted.full())
                throw ExportJobRepository.error(HttpStatus.FORBIDDEN);
            return admitted;
        } catch (StatusRuntimeException failure) {
            throw map(failure);
        }
    }

    private static ResponseStatusException map(StatusRuntimeException failure) {
        var status =
                switch (failure.getStatus().getCode()) {
                    case PERMISSION_DENIED, UNAUTHENTICATED, FAILED_PRECONDITION ->
                            HttpStatus.FORBIDDEN;
                    case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case RESOURCE_EXHAUSTED -> HttpStatus.TOO_MANY_REQUESTS;
                    default -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        return ExportJobRepository.error(status);
    }

    public record GeneratedFile(
            byte[] content,
            String contentType,
            int rowCount,
            boolean requiresFull,
            String teacherScopeHash) {
        @Override
        public String toString() {
            return "GeneratedExport[bytes=" + (content == null ? 0 : content.length) + "]";
        }
    }

    public record Download(String filename, String contentType, byte[] content) {
        @Override
        public String toString() {
            return "ExportDownload[bytes=" + content.length + "]";
        }
    }
}
