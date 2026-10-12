package ru.rutcampustrack.auth.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.dto.ExportJobModels.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.ReportDownloadTicketService;
import ru.rutcampustrack.auth.session.model.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real owning Flyway chain and transactions; synthetic authority token never leaves this fixture. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportJobsIT {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rct_exports").withUsername("rct_exports").withPassword("synthetic_exports_fixture")
            .withLabel("rct.validation.owner", "export_developer").withReuse(false);
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ObjectMapper mapper;
    private ExportJobService jobs;
    private SessionPrincipal actor;
    private ReportDownloadTicketService tickets;

    @BeforeAll void start() {
        postgres.start();
        Path root = Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.exists(root.resolve("settings.gradle.kts"))) root = root.getParent();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("filesystem:" + root.resolve("services/academic-service/academic-app/src/main/resources/db/migration")).load().migrate();
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds); tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        mapper = new ObjectMapper().findAndRegisterModules();
        tickets = mock(ReportDownloadTicketService.class);
        when(tickets.authorizeJob(any(), any(), any())).thenAnswer(call -> {
            SessionPrincipal p = call.getArgument(0); IssueReportDownloadTicketRequest report = call.getArgument(1); Instant expiry = call.getArgument(2);
            return new ReportDownloadTicketRedemptionResponse(new AuthAdmissionResponse("synthetic-internal-token", expiry, p.sessionId().toString(), Long.toString(p.userId()),
                    Long.toString(p.sessionVersion()), Long.toString(p.rolesVersion()), p.selectedRole().name(), p.selectedStatus().name(), null, false, false), expiry, report.bindingHash(), report);
        });
        jobs = new ExportJobService(jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets);
    }
    @AfterAll void stop() { postgres.stop(); }
    @BeforeEach void fixture() {
        jdbc.update("DELETE FROM auth_export_jobs");
        jdbc.update("DELETE FROM auth_outbox WHERE event_type='export.state.changed'");
        jdbc.update("DELETE FROM audit_log WHERE action='registry.export'");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE login='admin'",Long.class);
        long grant = jdbc.queryForObject("SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",Long.class,id);
        long rv = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?",Long.class,id);
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method) VALUES (?,?,?,1,?,NOW()+interval '1 day',NOW(),NOW(),'PASSWORD')",session,id,grant,UUID.randomUUID());
        jdbc.update("UPDATE admin_access_profiles SET version=version+1,assigned=true,mode='FULL',permissions='{}' WHERE user_id=?",id);
        rv = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?",Long.class,id);
        actor = new SessionPrincipal(id,session,1,rv,AuthRole.ADMIN,RoleStatus.ACTIVE,null,false,false);
    }
    private IssueReportDownloadTicketRequest users() { return new IssueReportDownloadTicketRequest(ReportDownloadKind.ADMIN_USERS,null,null,null,null,null,null,null,null,
            new IssueReportDownloadTicketRequest.AdminUsersParameters("synthetic",null,null,null,null,ReportDownloadFormat.HTML),null); }
    private Status enqueue() { return tx.execute(ignored -> jobs.enqueue(actor,new Create(UUID.randomUUID(),users()))); }
    private Claim claim() { return tx.execute(ignored -> jobs.claim().orElseThrow()); }
    private Complete complete(Claim claim, boolean full) { return new Complete(claim.leaseId(),"<html>synthetic registry</html>".getBytes(StandardCharsets.UTF_8),"text/html",3,full); }
    private long count(String sql) { return jdbc.queryForObject(sql,Long.class); }

    @Test void durableQueueSurvivesNewWorkerAndAbandonedLeaseWithoutPartialFileOrDuplicateAudit() {
        UUID request = UUID.randomUUID();
        Status queued = tx.execute(ignored -> jobs.enqueue(actor,new Create(request,users())));
        assertThat(queued.state()).isEqualTo(State.QUEUED);
        Status duplicate = tx.execute(ignored -> jobs.enqueue(actor,new Create(request,users())));
        assertThat(duplicate.jobId()).isEqualTo(queued.jobId());
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isEqualTo(1);
        Claim lost = claim();
        assertThat(jobs.status(actor,queued.jobId()).state()).isEqualTo(State.PREPARING);
        assertThatThrownBy(() -> jobs.file(actor,queued.jobId())).isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE auth_export_jobs SET lease_until=NOW()-interval '1 second' WHERE job_id=?",queued.jobId());
        jobs = new ExportJobService(jdbc,mapper,new ExportReportAuthority(tickets,jdbc),tickets);
        Claim recovered = claim();
        assertThat(recovered.jobId()).isEqualTo(lost.jobId()); assertThat(recovered.leaseId()).isNotEqualTo(lost.leaseId());
        assertThatThrownBy(() -> tx.execute(ignored -> jobs.complete(lost.jobId(),complete(lost,true)))).isInstanceOf(ResponseStatusException.class);
        tx.execute(ignored -> jobs.complete(recovered.jobId(),complete(recovered,true)));
        tx.execute(ignored -> jobs.complete(recovered.jobId(),complete(recovered,true)));
        assertThat(jobs.file(actor,queued.jobId()).content()).isEqualTo(complete(recovered,true).content());
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM auth_outbox WHERE payload->'payload'->>'state'='ready'")).isEqualTo(1);
        String identity = jdbc.queryForObject("SELECT identity::text FROM auth_export_jobs WHERE job_id=?",String.class,queued.jobId());
        assertThat(identity).doesNotContain("synthetic-internal-token", "password", "Bearer");
        String audit = jdbc.queryForObject("SELECT extras::text FROM audit_log WHERE action='registry.export'",String.class);
        assertThat(audit).contains("rowCount", "3", "jobId").doesNotContain("synthetic registry", "Telegram", "password");
    }

    @Test void fullFileRemainsProtectedAfterDowngradeEvenWithOrdinaryViewGrantAndForeignSessionIsHidden() {
        Status queued = enqueue(); Claim claim = claim(); tx.execute(ignored -> jobs.complete(claim.jobId(),complete(claim,true)));
        assertThat(jobs.file(actor,queued.jobId()).content()).isNotEmpty();
        var foreign = new SessionPrincipal(actor.userId(),UUID.randomUUID(),1,actor.rolesVersion(),AuthRole.ADMIN,RoleStatus.ACTIVE,null,false,false);
        assertThatThrownBy(() -> jobs.status(foreign,queued.jobId())).isInstanceOfSatisfying(ResponseStatusException.class,error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        jdbc.update("UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY['USERS_VIEW'] WHERE user_id=?",actor.userId());
        assertThatThrownBy(() -> jobs.status(actor,queued.jobId())).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor,queued.jobId())).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.ticket(actor,queued.jobId())).isInstanceOf(AdminAccessException.class);
    }

    @Test void ordinaryLimitedGrantWorksAndRevokeDeniesEveryLaterReadAndCompletingClaim() {
        jdbc.update("UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY['USERS_VIEW'] WHERE user_id=?",actor.userId());
        actor = new SessionPrincipal(actor.userId(),actor.sessionId(),actor.sessionVersion(),jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?",Long.class,actor.userId()),AuthRole.ADMIN,RoleStatus.ACTIVE,null,false,false);
        Status queued = enqueue(); Claim claim = claim();
        tx.execute(ignored -> jobs.complete(claim.jobId(),complete(claim,false)));
        assertThat(jobs.file(actor,queued.jobId()).content()).isNotEmpty();
        jdbc.update("UPDATE admin_access_profiles SET version=version+1,permissions=ARRAY['GROUPS_VIEW'] WHERE user_id=?",actor.userId());
        assertThatThrownBy(() -> jobs.status(actor,queued.jobId())).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor,queued.jobId())).isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> enqueue()).isInstanceOf(AdminAccessException.class);
    }

    @Test void failedOutboxInsertRollsBackReadyFileAndAuditThenRetryCommitsExactlyOnce() {
        Status queued = enqueue(); Claim claim = claim();
        jdbc.execute("ALTER TABLE auth_outbox ADD CONSTRAINT fixture_ready_failure CHECK (payload->'payload'->>'state' IS DISTINCT FROM 'ready')");
        try { assertThatThrownBy(() -> tx.execute(ignored -> jobs.complete(claim.jobId(),complete(claim,true)))).isInstanceOf(org.springframework.dao.DataAccessException.class); }
        finally { jdbc.execute("ALTER TABLE auth_outbox DROP CONSTRAINT fixture_ready_failure"); }
        assertThat(jobs.status(actor,queued.jobId()).state()).isEqualTo(State.PREPARING);
        assertThat(count("SELECT count(*) FROM auth_export_jobs WHERE content IS NOT NULL")).isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'")).isZero();
        tx.execute(ignored -> jobs.complete(claim.jobId(),complete(claim,true)));
        assertThat(jobs.status(actor,queued.jobId()).state()).isEqualTo(State.READY);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'")).isEqualTo(1);
    }

    @Test void threeGenerationFailuresTerminateAndWireAndDatabaseRejectUnknownOrOrdinalState() throws Exception {
        Status queued = enqueue();
        for(int i=0;i<3;i++){ Claim claim=claim(); tx.executeWithoutResult(ignored -> jobs.fail(claim.jobId(),new Fail(claim.leaseId(),"GENERATION_FAILED"))); }
        assertThat(jobs.status(actor,queued.jobId()).state()).isEqualTo(State.ERROR);
        assertThat(count("SELECT attempts FROM auth_export_jobs")).isEqualTo(3);
        assertThat(mapper.writeValueAsString(State.PREPARING)).isEqualTo("\"preparing\"");
        assertThat(mapper.readValue("\"ready\"",State.class)).isEqualTo(State.READY);
        assertThatThrownBy(() -> mapper.readValue("\"unknown\"",State.class)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE auth_export_jobs SET state='1'")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor,queued.jobId())).isInstanceOf(ResponseStatusException.class);
    }
}
