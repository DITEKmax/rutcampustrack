package ru.rutcampustrack.academic.exportjobs;

import io.grpc.StatusRuntimeException;

import jakarta.validation.Validator;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.Create;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.*;
import java.util.*;

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
