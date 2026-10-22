package ru.rutcampustrack.academic.exportjobs;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.exportjobs.AcademicExportJobModels.Status;
import ru.rutcampustrack.shared.events.AbstractEventPublisher;

import java.time.*;
import java.util.*;

/** Only short owner-local transactions. Callers must finish remote admission before entering. */
@Repository
public class ExportJobRepository extends AbstractEventPublisher {
    static final int MAX_BYTES = 20 * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ExportJobMetrics metrics;
    private final LocalReportAuthority local;

    public ExportJobRepository(
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            ExportJobMetrics metrics,
            LocalReportAuthority local) {
        super("academic-service");
        this.jdbc = jdbc;
        this.mapper = mapper.copy();
        this.metrics = metrics;
        this.local = local;
    }

    @Transactional
    public Job enqueue(UUID requestId, StoredReportIdentity identity, boolean full) {
        local.require(identity, false);
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210056L);
        expire(256);
        var previous =
                jdbc.query(
                        "SELECT job_id,binding_hash FROM academic_export_jobs WHERE user_id=? AND"
                                + " session_id=? AND request_id=?",
                        (rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2)),
                        identity.actor().userId(),
                        identity.actor().sessionId(),
                        requestId);
        if (!previous.isEmpty()) {
            if (!previous.getFirst().getValue().equals(identity.report().bindingHash()))
                throw error(HttpStatus.CONFLICT);
            return load(previous.getFirst().getKey());
        }
        Long personal =
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_export_jobs WHERE user_id=?",
                        Long.class,
                        identity.actor().userId());
        Long total = jdbc.queryForObject("SELECT count(*) FROM academic_export_jobs", Long.class);
        if (personal >= 10 || total >= 256) throw error(HttpStatus.TOO_MANY_REQUESTS);
        UUID id = UUID.randomUUID();
        // Database timestamp is the retention origin, including time spent admitting this request.
        jdbc.update(
                "INSERT INTO"
                    + " academic_export_jobs(job_id,user_id,session_id,request_id,binding_hash,identity,requires_full,expires_at)"
                    + " VALUES (?,?,?,?,?,?::jsonb,?,LEAST(?,NOW()+interval '10 minutes'))",
                id,
                identity.actor().userId(),
                identity.actor().sessionId(),
                requestId,
                identity.report().bindingHash(),
                json(identity),
                full,
                at(identity.expiresAt()));
        event(id, identity, "queued");
        committed(() -> metrics.accepted(identity.report().kind()));
        return load(id);
    }

    public Optional<Job> candidate() {
        var ids =
                jdbc.query(
                        "SELECT job_id FROM academic_export_jobs WHERE expires_at>NOW() AND"
                            + " attempts<3 AND ((state='queued' AND (lease_until IS NULL OR"
                            + " lease_until<=NOW())) OR (state='preparing' AND lease_until<NOW()))"
                            + " ORDER BY created_at,job_id LIMIT 1",
                        (rs, n) -> rs.getObject(1, UUID.class));
        return ids.isEmpty() ? Optional.empty() : Optional.of(load(ids.getFirst()));
    }

    @Transactional
    public Optional<Job> claim(Job admitted, boolean full) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210057L);
        terminateAbandoned();
        Long active =
                jdbc.queryForObject(
                        "SELECT count(*) FROM academic_export_jobs WHERE state='preparing' AND"
                                + " lease_until>NOW() AND expires_at>NOW()",
                        Long.class);
        if (active >= 2) return Optional.empty();
        lock(admitted.id());
        Job job = load(admitted.id());
        if (job.attempts() >= 3
                || !(job.state().equals("queued") || job.state().equals("preparing"))
                || job.leaseUntil() != null && job.leaseUntil().isAfter(Instant.now()))
            return Optional.empty();
        local.require(job.identity(), false);
        UUID lease = UUID.randomUUID();
        jdbc.update(
                "UPDATE academic_export_jobs SET"
                    + " state='preparing',attempts=attempts+1,lease_id=?,lease_until=NOW()+interval"
                    + " '90 seconds',requires_full=requires_full OR ? WHERE job_id=?",
                lease,
                full,
                job.id());
        event(job.id(), job.identity(), "preparing");
        return Optional.of(load(job.id()));
    }

    @Transactional
    public Job complete(
            UUID id,
            UUID lease,
            byte[] bytes,
            String contentType,
            int rows,
            boolean full,
            StoredReportIdentity captured) {
        lock(id);
        Job job = load(id);
        if (job.state().equals("ready") && Objects.equals(job.leaseId(), lease)) {
            local.require(job.identity(), true);
            return job;
        }
        requireLease(job, lease);
        if (!job.identity().actor().equals(captured.actor())
                || !job.identity().report().bindingHash().equals(captured.report().bindingHash()))
            throw error(HttpStatus.CONFLICT);
        local.require(captured, true);
        if (bytes == null
                || bytes.length == 0
                || bytes.length > MAX_BYTES
                || rows < 0
                || rows > 10000
                || !org.springframework.http.MediaType.parseMediaType(
                                job.identity().report().expectedMediaType())
                        .isCompatibleWith(
                                org.springframework.http.MediaType.parseMediaType(contentType)))
            throw error(HttpStatus.BAD_REQUEST);
        var arguments =
                new ArrayList<Object>(
                        List.of(json(captured), bytes, contentType, rows, full, id, lease));
        arguments.addAll(readyGuardArguments(captured));
        int changed =
                jdbc.update(
                        "UPDATE academic_export_jobs SET"
                            + " state='ready',identity=?::jsonb,content=?,content_type=?,row_count=?,requires_full=requires_full"
                            + " OR ?,lease_until=NULL,error_code=NULL WHERE job_id=? AND"
                            + " state='preparing' AND lease_id=? AND lease_until>NOW() AND"
                            + " expires_at>NOW()"
                                + READY_GUARD,
                        arguments.toArray());
        if (changed != 1) throw error(HttpStatus.CONFLICT);
        if (job.identity().report().kind().name().startsWith("ADMIN_"))
            jdbc.update(
                    "INSERT INTO audit_log(user_id,action,target_type,extras,succeeded,created_at)"
                            + " VALUES (?,'registry.export',?,?::jsonb,TRUE,NOW())",
                    job.identity().actor().userId(),
                    job.identity().report().kind().code(),
                    json(
                            Map.of(
                                    "kind",
                                    job.identity().report().kind().code(),
                                    "rowCount",
                                    rows,
                                    "jobId",
                                    id.toString())));
        event(id, captured, "ready");
        committed(() -> metrics.terminal(captured.report().kind(), null, job.createdAt()));
        return load(id);
    }

    @Transactional
    public void fail(UUID id, UUID lease, String code) {
        lock(id);
        Job job = load(id);
        if (job.state().equals("error") && Objects.equals(job.leaseId(), lease)) return;
        requireLease(job, lease);
        if (!Set.of("AUTHORITY_DENIED", "GENERATION_FAILED", "EXPORT_TOO_LARGE").contains(code))
            throw error(HttpStatus.BAD_REQUEST);
        Instant due = Instant.now().plusSeconds(job.attempts() == 1 ? 5 : 10);
        if (!code.equals("GENERATION_FAILED")
                || job.attempts() >= 3
                || !due.isBefore(job.identity().expiresAt())) terminal(job, code);
        else {
            jdbc.update(
                    "UPDATE academic_export_jobs SET state='queued',lease_id=NULL,lease_until=?"
                            + " WHERE job_id=?",
                    at(due),
                    id);
            event(id, job.identity(), "queued");
            committed(() -> metrics.retried(job.identity().report().kind()));
        }
    }

    @Transactional
    public void deny(Job observed) {
        lock(observed.id());
        Job job = load(observed.id());
        // Never invalidate an already claimed newer attempt based on an older observation.
        if (Objects.equals(observed.leaseId(), job.leaseId())
                && observed.state().equals(job.state())) terminal(job, "AUTHORITY_DENIED");
    }

    public Job load(UUID id) {
        var rows =
                jdbc.query(
                        "SELECT"
                            + " job_id,request_id,identity,state,attempts,lease_id,lease_until,requires_full,content_type,error_code,created_at"
                            + " FROM academic_export_jobs WHERE job_id=? AND expires_at>NOW()",
                        (rs, n) ->
                                new Job(
                                        rs.getObject("job_id", UUID.class),
                                        rs.getObject("request_id", UUID.class),
                                        decode(rs.getString("identity")),
                                        rs.getString("state"),
                                        rs.getInt("attempts"),
                                        rs.getObject("lease_id", UUID.class),
                                        instant(rs.getObject("lease_until", OffsetDateTime.class)),
                                        rs.getBoolean("requires_full"),
                                        rs.getString("content_type"),
                                        rs.getString("error_code"),
                                        rs.getObject("created_at", OffsetDateTime.class)
                                                .toInstant()),
                        id);
        if (rows.size() != 1) throw error(HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    public byte[] content(Job job) {
        var arguments = new ArrayList<Object>();
        arguments.add(job.id());
        arguments.addAll(readyGuardArguments(job.identity()));
        var rows =
                jdbc.query(
                        "SELECT content FROM academic_export_jobs WHERE job_id=? AND state='ready'"
                                + " AND expires_at>NOW()"
                                + READY_GUARD,
                        (rs, n) -> rs.getBytes(1),
                        arguments.toArray());
        if (rows.size() != 1 || rows.getFirst() == null || rows.getFirst().length == 0) {
            local.require(job.identity(), true);
            throw error(HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    // These predicates share the final write/content-read statement snapshot. A peer group
    // operation cannot expose its tentative membership through a previously rendered artifact.
    // Availability guards deny while any row exists, including retained FAILED_SAFE operations.
    private static final String READY_GUARD =
            """
 AND EXISTS(SELECT 1 FROM academic_profiles p WHERE p.id=?
   AND p.academic_revision=? AND p.applied_auth_roles_version=? AND p.archived_at IS NULL)
 AND NOT EXISTS(SELECT 1 FROM academic_identity_operation_targets t
   JOIN academic_identity_operations o ON o.operation_id=t.operation_id
   WHERE t.fenced AND o.phase<>'SUCCEEDED' AND (? OR t.user_id=?))
 AND NOT EXISTS(SELECT 1 FROM academic_identity_operation_groups g
   JOIN academic_identity_operations o ON o.operation_id=g.operation_id
   WHERE g.fenced AND o.phase<>'SUCCEEDED' AND (? OR g.group_id=ANY(?::bigint[])))
 AND NOT EXISTS(SELECT 1 FROM academic_group_availability_headmen WHERE ? OR user_id=?)
 AND NOT EXISTS(SELECT 1 FROM academic_group_availability_guards WHERE ? OR group_id=ANY(?::bigint[]))
""";

    private static List<Object> readyGuardArguments(StoredReportIdentity identity) {
        var groups = new TreeSet<Long>();
        var p = identity.report();
        if (identity.actor().groupId() != null) groups.add(identity.actor().groupId());
        if (p.kind() == ru.rutcampustrack.auth.dto.ReportDownloadKind.TEACHER_JOURNAL)
            groups.add(p.teacherJournal().groupId());
        if (p.kind() == ru.rutcampustrack.auth.dto.ReportDownloadKind.TEACHER_STATS) {
            if (p.teacherStats().groupId() != null) groups.add(p.teacherStats().groupId());
            if (identity.teacherScope() != null)
                identity.teacherScope().tuples().forEach(tuple -> groups.add(tuple.groupId()));
        }
        String groupArray =
                groups.stream()
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        boolean registry = p.kind().name().startsWith("ADMIN_");
        return List.of(
                identity.actor().userId(),
                identity.academicRevision(),
                identity.actor().rolesVersion(),
                registry,
                identity.actor().userId(),
                registry,
                groupArray,
                registry,
                identity.actor().userId(),
                registry,
                groupArray);
    }

    @Transactional
    public void cleanExpired() {
        terminateAbandoned();
        expire(32);
    }

    @Transactional
    public void recoverAbandoned() {
        terminateAbandoned();
    }

    private void terminateAbandoned() {
        var abandoned =
                jdbc.query(
                        "SELECT job_id FROM academic_export_jobs WHERE state='preparing' AND"
                            + " lease_until<NOW() AND attempts>=3 AND expires_at>NOW() FOR UPDATE"
                            + " SKIP LOCKED",
                        (rs, n) -> rs.getObject(1, UUID.class));
        for (UUID id : abandoned) terminal(load(id), "GENERATION_FAILED");
    }

    private void expire(int limit) {
        jdbc.query(
                "DELETE FROM academic_export_jobs WHERE job_id IN (SELECT job_id FROM"
                        + " academic_export_jobs WHERE expires_at<=NOW() ORDER BY expires_at,job_id"
                        + " LIMIT ? FOR UPDATE SKIP LOCKED) RETURNING identity,state,created_at",
                rs -> {
                    if (Set.of("queued", "preparing").contains(rs.getString("state"))) {
                        var identity = decode(rs.getString("identity"));
                        Instant created =
                                rs.getObject("created_at", OffsetDateTime.class).toInstant();
                        committed(() -> metrics.expired(identity.report().kind().code(), created));
                    }
                },
                limit);
    }

    private void terminal(Job job, String code) {
        jdbc.update(
                "UPDATE academic_export_jobs SET"
                    + " state='error',content=NULL,error_code=?,lease_until=NULL WHERE job_id=?",
                code,
                job.id());
        event(job.id(), job.identity(), "error");
        committed(() -> metrics.terminal(job.identity().report().kind(), code, job.createdAt()));
    }

    private void lock(UUID id) {
        jdbc.queryForList("SELECT job_id FROM academic_export_jobs WHERE job_id=? FOR UPDATE", id);
    }

    static void requireLease(Job job, UUID lease) {
        if (!job.state().equals("preparing")
                || !Objects.equals(job.leaseId(), lease)
                || job.leaseUntil() == null
                || !Instant.now().isBefore(job.leaseUntil())) throw error(HttpStatus.CONFLICT);
    }

    private void event(UUID id, StoredReportIdentity identity, String state) {
        var event =
                fillDefaults(
                        new ExportStateEvent(
                                this,
                                id,
                                identity.actor().userId(),
                                identity.actor().sessionId(),
                                state));
        jdbc.update(
                "INSERT INTO academic_outbox(event_type,payload) VALUES (?,?::jsonb)",
                event.getEventType(),
                json(event));
    }

    private StoredReportIdentity decode(String value) {
        try {
            return mapper.readValue(value, StoredReportIdentity.class);
        } catch (Exception failure) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception failure) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static OffsetDateTime at(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static void committed(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Report mutation requires transaction");
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    static ResponseStatusException error(HttpStatus status) {
        return new ResponseStatusException(status, "Операция с выгрузкой недоступна");
    }

    public record Job(
            UUID id,
            UUID requestId,
            StoredReportIdentity identity,
            String state,
            int attempts,
            UUID leaseId,
            Instant leaseUntil,
            boolean requiresFull,
            String contentType,
            String errorCode,
            Instant createdAt) {
        public Status status() {
            return new Status(
                    id,
                    ru.rutcampustrack.auth.dto.ExportJobModels.State.from(state),
                    identity.report().suggestedFilename(),
                    identity.expiresAt(),
                    errorCode);
        }

        @Override
        public String toString() {
            return "ExportJob[id=" + id + ",state=" + state + "]";
        }
    }
}
