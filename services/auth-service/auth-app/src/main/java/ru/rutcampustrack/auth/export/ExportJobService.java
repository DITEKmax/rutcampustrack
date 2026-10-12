package ru.rutcampustrack.auth.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private static final Duration JOB_WINDOW = Duration.ofMinutes(10);
    private static final Duration LEASE = Duration.ofSeconds(90);
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExportReportAuthority authority;
    private final ReportDownloadTicketService tickets;

    public ExportJobService(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            ExportReportAuthority authority,
            ReportDownloadTicketService tickets) {
        super("auth-service");
        this.jdbc = jdbc;
        this.mapper = mapper.copy();
        this.authority = authority;
        this.tickets = tickets;
    }

    @Transactional
    public Status enqueue(SessionPrincipal actor, Create request) {
        Instant expires = Instant.now().plus(JOB_WINDOW);
        authority.admit(actor, request.report(), expires, false);
        boolean full = authority.requiresFull(actor, request.report());
        // Bounds and idempotency are serialized independently of business mutation locks.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210056L);
        jdbc.update("DELETE FROM auth_export_jobs WHERE expires_at < NOW()");
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
        // A crashed worker's lease is recoverable; the third abandoned attempt terminates
        // explicitly.
        var abandoned =
                jdbc.query(
                        "SELECT job_id FROM auth_export_jobs WHERE state='preparing' AND lease_until<NOW() AND attempts>=3 AND expires_at>NOW() FOR UPDATE SKIP LOCKED",
                        (rs, n) -> rs.getObject(1, UUID.class));
        for (UUID id : abandoned) terminal(load(id), "GENERATION_FAILED");
        var candidates =
                jdbc.query(
                        "SELECT job_id FROM auth_export_jobs WHERE expires_at>NOW() AND attempts<3 AND (state='queued' OR state='preparing' AND lease_until<NOW()) ORDER BY created_at,job_id LIMIT 1 FOR UPDATE SKIP LOCKED",
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
        return Optional.of(new Claim(job.id, lease, identity));
    }

    @Transactional
    public Status complete(UUID id, Complete completed) {
        lock(id);
        Job job = load(id);
        if (job.state == State.READY && Objects.equals(job.leaseId, completed.leaseId()))
            return response(job);
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
        jdbc.update(
                "UPDATE auth_export_jobs SET state='ready',content=?,content_type=?,row_count=?,requires_full=?,lease_until=NULL,error_code=NULL WHERE job_id=?",
                completed.content(),
                completed.contentType(),
                completed.rowCount(),
                full,
                id);
        if (job.identity.report().kind() == ReportDownloadKind.ADMIN_USERS
                || job.identity.report().kind() == ReportDownloadKind.ADMIN_GROUPS) {
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
            jdbc.update(
                    "UPDATE auth_export_jobs SET state='queued',lease_id=NULL,lease_until=NULL WHERE job_id=?",
                    id);
            event(id, job.identity, State.QUEUED);
        }
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
    }

    private Job load(UUID id) {
        var rows =
                jdbc.query(
                        "SELECT job_id,identity,state,lease_id,lease_until,attempts,requires_full,content_type,error_code FROM auth_export_jobs WHERE job_id=? AND expires_at>NOW()",
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
                                    rs.getString("error_code"));
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
            String errorCode) {}

    public record Download(String filename, String contentType, byte[] content) {
        @Override
        public String toString() {
            return "ExportDownload[bytes=" + content.length + "]";
        }
    }
}
