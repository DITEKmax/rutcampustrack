package ru.rutcampustrack.auth.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.dto.ExportJobModels.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.auth.service.StoredReportDownloadTicket;
import ru.rutcampustrack.auth.session.model.*;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

/** Durable queue and completed-file owner; no caller credentials are persisted. */
@Service
public class ExportJobService extends AbstractEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ExportJobService.class);
    private static final Pattern REQUEST_ID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-f]{16}");
    private static final Duration JOB_WINDOW = Duration.ofMinutes(10);
    private static final Duration LEASE = Duration.ofSeconds(90);
    private static final int MAX_ACTIVE_JOBS = 2;
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExportReportAuthority authority;
    private final ReportDownloadTicketService tickets;
    private final ExportJobMetrics metrics;

    public ExportJobService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            ExportReportAuthority authority,
            ReportDownloadTicketService tickets,
            ExportJobMetrics metrics) {
        super("auth-service");
        this.jdbc = jdbc;
        this.mapper = mapper.copy();
        this.authority = authority;
        this.tickets = tickets;
        this.metrics = metrics;
    }

    @Transactional
    public Status enqueue(SessionPrincipal actor, Create request) {
        Instant expires = Instant.now().plus(JOB_WINDOW);
        authority.admit(actor, request.report(), expires, false);
        boolean full = authority.requiresFull(actor, request.report());
        // Bounds and idempotency are serialized independently of business mutation locks.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210056L);
        expire(256);
        var previous =
                jdbc.query(
                        "SELECT job_id, binding_hash FROM auth_export_jobs WHERE user_id=? AND session_id=? AND request_id=?",
                        (rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)),
                        actor.userId(),
                        actor.sessionId(),
                        request.requestId());
        if (!previous.isEmpty()) {
            if (!previous.getFirst().getValue().equals(request.report().bindingHash()))
                throw error(HttpStatus.CONFLICT, "Повтор имеет другой разрез");
            return status(actor, previous.getFirst().getKey());
        }
        Long personal =
                jdbc.queryForObject(
                        "SELECT count(*) FROM auth_export_jobs WHERE user_id=?",
                        Long.class,
                        actor.userId());
        Long total = jdbc.queryForObject("SELECT count(*) FROM auth_export_jobs", Long.class);
        if (personal >= 10 || total >= 256)
            throw error(HttpStatus.TOO_MANY_REQUESTS, "Дождись завершения предыдущих выгрузок");
        UUID id = UUID.randomUUID();
        var identity =
                new StoredReportDownloadTicket(
                        1,
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        actor.rolesVersion(),
                        actor.selectedRole(),
                        actor.selectedStatus(),
                        actor.groupId(),
                        actor.headman(),
                        actor.readOnly(),
                        request.report(),
                        expires);
        jdbc.update(
                "INSERT INTO auth_export_jobs(job_id,user_id,session_id,request_id,binding_hash,identity,requires_full,expires_at) VALUES (?,?,?,?,?,?::jsonb,?,?)",
                id,
                actor.userId(),
                actor.sessionId(),
                request.requestId(),
                request.report().bindingHash(),
                json(identity),
                full,
                expires.atOffset(ZoneOffset.UTC));
        event(id, identity, State.QUEUED);
        committed(() -> metrics.accepted(request.report().kind()));
        lifecycle("accepted", id, request.requestId(), 0, State.QUEUED, "NONE");
        return new Status(id, State.QUEUED, request.report().suggestedFilename(), expires, null);
    }

    public Status status(SessionPrincipal actor, UUID id) {
        Job job = owned(actor, id);
        authorize(actor, job);
        return response(job);
    }

    public Download file(SessionPrincipal actor, UUID id) {
        Job job = owned(actor, id);
        authorize(actor, job);
        if (job.state != State.READY) throw error(HttpStatus.CONFLICT, "Файл ещё не готов");
        byte[] content;
        try {
            content =
                    jdbc.queryForObject(
                            "SELECT content FROM auth_export_jobs WHERE job_id=? AND state='ready' AND expires_at>NOW()",
                            byte[].class,
                            id);
        } catch (EmptyResultDataAccessException expiredOrRemoved) {
            throw error(HttpStatus.NOT_FOUND, "Выгрузка не найдена");
        }
        if (content == null || content.length == 0 || content.length > MAX_BYTES)
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Файл недоступен");
        // A second authority read covers a change while the bytea was loaded.
        authorize(actor, job);
        return new Download(job.identity.report().suggestedFilename(), job.contentType, content);
    }

    public ReportDownloadTicketResponse ticket(SessionPrincipal actor, UUID id) {
        Job job = owned(actor, id);
        authorize(actor, job);
        if (job.state != State.READY) throw error(HttpStatus.CONFLICT, "Файл ещё не готов");
        return tickets.issueReadyJob(actor, job.identity.report(), id, job.identity.expiresAt());
    }

    @Transactional
    public Optional<Claim> claim() {
        // Serialize admission across Auth/Gateway replicas, independently of enqueue limits.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210057L);
        // A crashed worker's lease is recoverable; the third abandoned attempt terminates
        // explicitly.
        var abandoned =
                jdbc.query(
                        "SELECT job_id FROM auth_export_jobs WHERE state='preparing' AND lease_until<NOW() AND attempts>=3 AND expires_at>NOW() FOR UPDATE SKIP LOCKED",
                        (rs, n) -> rs.getObject(1, UUID.class));
        for (UUID id : abandoned) terminal(load(id), "GENERATION_FAILED");
        Long active =
                jdbc.queryForObject(
                        "SELECT count(*) FROM auth_export_jobs WHERE state='preparing' AND lease_until>NOW() AND expires_at>NOW()",
                        Long.class);
        if (active >= MAX_ACTIVE_JOBS) return Optional.empty();
        var candidates =
                jdbc.query(
                        "SELECT job_id FROM auth_export_jobs WHERE expires_at>NOW() AND attempts<3 AND ((state='queued' AND (lease_until IS NULL OR lease_until<=NOW())) OR (state='preparing' AND lease_until<NOW())) ORDER BY created_at,job_id LIMIT 1 FOR UPDATE SKIP LOCKED",
                        (rs, n) -> rs.getObject(1, UUID.class));
        if (candidates.isEmpty()) return Optional.empty();
        Job job = load(candidates.getFirst());
        final ReportDownloadTicketRedemptionResponse identity;
        final boolean full;
        try {
            identity =
                    authority.admit(
                            job.identity.principal(),
                            job.identity.report(),
                            job.identity.expiresAt(),
                            job.requiresFull);
            full =
                    job.requiresFull
                            || authority.requiresFull(
                                    job.identity.principal(), job.identity.report());
        } catch (ru.rutcampustrack.auth.session.AuthSessionException denied) {
            if (denied.code()
                    == ru.rutcampustrack.auth.session.AuthSessionException.Code
                            .AUTHORITY_UNAVAILABLE) throw denied;
            terminal(job, "AUTHORITY_DENIED");
            return Optional.empty();
        } catch (ru.rutcampustrack.auth.session.SessionAdmissionException denied) {
            if (denied.code()
                    == ru.rutcampustrack.auth.session.SessionAdmissionException.Code
                            .AUTHORITY_UNAVAILABLE) throw denied;
            terminal(job, "AUTHORITY_DENIED");
            return Optional.empty();
        } catch (ru.rutcampustrack.shared.security.admin.AdminAccessException denied) {
            if (denied.getStatusCode().value() == 503) throw denied;
            terminal(job, "AUTHORITY_DENIED");
            return Optional.empty();
        }
        UUID lease = UUID.randomUUID();
        jdbc.update(
                "UPDATE auth_export_jobs SET state='preparing',attempts=attempts+1,lease_id=?,lease_until=?,requires_full=? WHERE job_id=?",
                lease,
                Instant.now().plus(LEASE).atOffset(ZoneOffset.UTC),
                full,
                job.id);
        event(job.id, job.identity, State.PREPARING);
        lifecycle(
                "claimed",
                job.id,
                job.operationRequestId,
                job.attempts + 1,
                State.PREPARING,
                "NONE");
        return Optional.of(new Claim(job.id, lease, identity));
    }

    @Transactional
    public Status complete(UUID id, Complete completed) {
        lock(id);
        Job job = load(id);
        if (job.state == State.READY && Objects.equals(job.leaseId, completed.leaseId())) {
            authorize(job.identity.principal(), job);
            return response(job);
        }
        requireLease(job, completed.leaseId());
        boolean full = job.requiresFull || completed.requiresFull();
        authority.admit(
                job.identity.principal(), job.identity.report(), job.identity.expiresAt(), full);
        MediaType actual;
        try {
            actual = MediaType.parseMediaType(completed.contentType());
        } catch (RuntimeException invalid) {
            throw error(HttpStatus.BAD_REQUEST, "Некорректный формат результата");
        }
        if (!MediaType.parseMediaType(job.identity.report().expectedMediaType())
                        .isCompatibleWith(actual)
                || completed.content() == null
                || completed.content().length == 0
                || completed.content().length > MAX_BYTES) {
            throw error(HttpStatus.BAD_REQUEST, "Некорректный результат выгрузки");
        }
        var captured =
                authority.captureTeacherScope(
                        job.identity.principal(),
                        job.identity.report(),
                        completed.teacherScopeHash());
        var completedIdentity = job.identity.withTeacherScope(captured);
        jdbc.update(
                "UPDATE auth_export_jobs SET state='ready',identity=?::jsonb,content=?,content_type=?,row_count=?,requires_full=?,lease_until=NULL,error_code=NULL WHERE job_id=?",
                json(completedIdentity),
                completed.content(),
                completed.contentType(),
                completed.rowCount(),
                full,
                id);
        if (job.identity.report().kind() == ReportDownloadKind.ADMIN_USERS
                || job.identity.report().kind() == ReportDownloadKind.ADMIN_GROUPS
                || job.identity.report().kind() == ReportDownloadKind.ADMIN_GROUP_ATTENDANCE) {
            jdbc.update(
                    "INSERT INTO audit_log(user_id,action,target_type,extras,succeeded,created_at) VALUES (?,'registry.export',?,?::jsonb,TRUE,NOW())",
                    job.identity.userId(),
                    job.identity.report().kind().code(),
                    json(
                            Map.of(
                                    "kind",
                                    job.identity.report().kind().code(),
                                    "rowCount",
                                    completed.rowCount(),
                                    "jobId",
                                    id.toString())));
        }
        event(id, job.identity, State.READY);
        committed(() -> metrics.terminal(job.identity.report().kind(), null, job.createdAt));
        lifecycle("completed", job.id, job.operationRequestId, job.attempts, State.READY, "NONE");
        return response(load(id));
    }

    @Transactional
    public void fail(UUID id, Fail failure) {
        lock(id);
        Job job = load(id);
        if (job.state == State.ERROR && Objects.equals(job.leaseId, failure.leaseId())) return;
        requireLease(job, failure.leaseId());
        if (job.attempts >= 3 || !"GENERATION_FAILED".equals(failure.code()))
            terminal(job, failure.code());
        else {
            // QUEUED has no lease owner: lease_until is its durable retry not-before time.
            Instant due = Instant.now().plusSeconds(5L << (job.attempts - 1));
            if (!due.isBefore(job.identity.expiresAt())) {
                terminal(job, "GENERATION_FAILED");
                return;
            }
            jdbc.update(
                    "UPDATE auth_export_jobs SET state='queued',lease_id=NULL,lease_until=? WHERE job_id=?",
                    due.atOffset(ZoneOffset.UTC),
                    id);
            event(id, job.identity, State.QUEUED);
            committed(() -> metrics.retried(job.identity.report().kind()));
            lifecycle(
                    "retry_scheduled",
                    job.id,
                    job.operationRequestId,
                    job.attempts,
                    State.QUEUED,
                    "GENERATION_FAILED");
        }
    }

    /** Expired bytea must be reclaimed even when no new request is enqueued. */
    @Scheduled(fixedDelayString = "${rutcampustrack.export-jobs.cleanup-millis:60000}")
    @Transactional
    public void cleanExpired() {
        expire(32);
    }

    private void expire(int limit) {
        jdbc.query(
                "DELETE FROM auth_export_jobs WHERE job_id IN (SELECT job_id FROM auth_export_jobs WHERE expires_at<=NOW() ORDER BY expires_at,job_id LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING job_id,request_id,attempts,identity->'report'->>'kind' AS kind,state,created_at",
                rs -> {
                    if ("queued".equals(rs.getString("state"))
                            || "preparing".equals(rs.getString("state"))) {
                        String kind = rs.getString("kind");
                        Instant createdAt =
                                rs.getObject("created_at", OffsetDateTime.class).toInstant();
                        committed(() -> metrics.expired(kind, createdAt));
                        lifecycle(
                                "expired",
                                rs.getObject("job_id", UUID.class),
                                rs.getObject("request_id", UUID.class),
                                rs.getInt("attempts"),
                                State.from(rs.getString("state")),
                                "EXPIRED");
                    }
                },
                limit);
    }

    private Job owned(SessionPrincipal actor, UUID id) {
        Job job = load(id);
        if (job.identity.userId() != actor.userId()
                || !job.identity.sessionId().equals(actor.sessionId()))
            throw error(HttpStatus.NOT_FOUND, "Выгрузка не найдена");
        return job;
    }

    private void authorize(SessionPrincipal actor, Job job) {
        if (!actor.equals(job.identity.principal()))
            throw error(HttpStatus.FORBIDDEN, "Доступ изменился");
        authority.admit(actor, job.identity.report(), job.identity.expiresAt(), job.requiresFull);
        if (job.state == State.READY) authority.requireReady(job.identity);
    }

    private Job load(UUID id) {
        var rows =
                jdbc.query(
                        "SELECT job_id,identity,state,lease_id,lease_until,attempts,requires_full,content_type,error_code,created_at,request_id FROM auth_export_jobs WHERE job_id=? AND expires_at>NOW()",
                        (rs, n) -> {
                            StoredReportDownloadTicket identity;
                            try {
                                identity =
                                        mapper.readValue(
                                                rs.getString("identity"),
                                                StoredReportDownloadTicket.class);
                            } catch (Exception e) {
                                throw error(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "Состояние выгрузки недоступно");
                            }
                            return new Job(
                                    rs.getObject("job_id", UUID.class),
                                    identity,
                                    State.from(rs.getString("state")),
                                    rs.getObject("lease_id", UUID.class),
                                    rs.getObject("lease_until", OffsetDateTime.class),
                                    rs.getInt("attempts"),
                                    rs.getBoolean("requires_full"),
                                    rs.getString("content_type"),
                                    rs.getString("error_code"),
                                    rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                                    rs.getObject("request_id", UUID.class));
                        },
                        id);
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "Выгрузка не найдена");
        return rows.getFirst();
    }

    private void lock(UUID id) {
        jdbc.queryForList("SELECT job_id FROM auth_export_jobs WHERE job_id=? FOR UPDATE", id);
    }

    private static void requireLease(Job job, UUID lease) {
        if (job.state != State.PREPARING
                || !Objects.equals(job.leaseId, lease)
                || job.leaseUntil == null
                || !Instant.now().isBefore(job.leaseUntil.toInstant())) {
            throw error(HttpStatus.CONFLICT, "Задание уже обработано или lease истёк");
        }
    }

    private void terminal(Job job, String code) {
        jdbc.update(
                "UPDATE auth_export_jobs SET state='error',content=NULL,error_code=?,lease_until=NULL WHERE job_id=?",
                code,
                job.id);
        event(job.id, job.identity, State.ERROR);
        committed(() -> metrics.terminal(job.identity.report().kind(), code, job.createdAt));
        lifecycle(
                "failed",
                job.id,
                job.operationRequestId,
                job.attempts,
                State.ERROR,
                safeCode(code));
    }

    private void lifecycle(
            String stage,
            UUID jobId,
            UUID operationRequestId,
            int attempt,
            State state,
            String code) {
        // Capture only validated diagnostic IDs while the HTTP/worker context is still current.
        // The operation request ID is an idempotency key; it never substitutes for an HTTP/trace
        // ID.
        String httpRequestId = diagnosticId(MDC.get("requestId"), REQUEST_ID);
        String traceId = diagnosticId(MDC.get("traceId"), TRACE_ID);
        String spanId = diagnosticId(MDC.get("spanId"), SPAN_ID);
        committed(
                () ->
                        log.info(
                                "Export job stage={} jobId={} operationRequestId={} attempt={} state={} code={} httpRequestId={} traceId={} spanId={}",
                                stage,
                                jobId,
                                operationRequestId,
                                attempt,
                                state.code(),
                                code,
                                httpRequestId,
                                traceId,
                                spanId));
    }

    private static String diagnosticId(String value, Pattern pattern) {
        return value != null && pattern.matcher(value).matches() ? value : "none";
    }

    private static String safeCode(String code) {
        return switch (code) {
            case "AUTHORITY_DENIED", "GENERATION_FAILED", "EXPORT_TOO_LARGE" -> code;
            default -> "GENERATION_FAILED";
        };
    }

    private static void committed(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Export job mutation requires a transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private void event(UUID id, StoredReportDownloadTicket identity, State state) {
        var event =
                fillDefaults(
                        new ExportStateEvent(
                                this, id, identity.userId(), identity.sessionId(), state.code()));
        jdbc.update(
                "INSERT INTO auth_outbox(event_type,payload) VALUES (?,?::jsonb)",
                event.getEventType(),
                json(event));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Состояние выгрузки недоступно");
        }
    }

    private static Status response(Job job) {
        return new Status(
                job.id,
                job.state,
                job.identity.report().suggestedFilename(),
                job.identity.expiresAt(),
                job.errorCode);
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }

    private record Job(
            UUID id,
            StoredReportDownloadTicket identity,
            State state,
            UUID leaseId,
            OffsetDateTime leaseUntil,
            int attempts,
            boolean requiresFull,
            String contentType,
            String errorCode,
            Instant createdAt,
            UUID operationRequestId) {}

    public record Download(String filename, String contentType, byte[] content) {
        @Override
        public String toString() {
            return "ExportDownload[bytes=" + content.length + "]";
        }
    }
}
