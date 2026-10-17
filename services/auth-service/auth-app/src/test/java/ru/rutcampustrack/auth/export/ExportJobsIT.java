package ru.rutcampustrack.auth.export;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
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

/**
 * Real owning Flyway chain and transactions; synthetic authority token never leaves this fixture.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportJobsIT {
    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("rct_exports")
                    .withUsername("rct_exports")
                    .withPassword("synthetic_exports_fixture")
                    .withLabel("rct.validation.owner", "export_developer")
                    .withReuse(false);
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private ObjectMapper mapper;
    private ExportJobService jobs;
    private SessionPrincipal actor;
    private ReportDownloadTicketService tickets;
    private SimpleMeterRegistry registry;
    private ExportJobMetrics metrics;

    @BeforeAll
    void start() {
        postgres.start();
        Path root = Path.of("").toAbsolutePath();
        while (!java.nio.file.Files.exists(root.resolve("settings.gradle.kts")))
            root = root.getParent();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations(
                        "filesystem:"
                                + root.resolve(
                                        "services/academic-service/academic-app/src/main/resources/db/migration"))
                .load()
                .migrate();
        var ds =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        mapper = new ObjectMapper().findAndRegisterModules();
        tickets = mock(ReportDownloadTicketService.class);
        when(tickets.authorizeJob(any(), any(), any()))
                .thenAnswer(
                        call -> {
                            SessionPrincipal p = call.getArgument(0);
                            IssueReportDownloadTicketRequest report = call.getArgument(1);
                            Instant expiry = call.getArgument(2);
                            return new ReportDownloadTicketRedemptionResponse(
                                    new AuthAdmissionResponse(
                                            "synthetic-internal-token",
                                            expiry,
                                            p.sessionId().toString(),
                                            Long.toString(p.userId()),
                                            Long.toString(p.sessionVersion()),
                                            Long.toString(p.rolesVersion()),
                                            p.selectedRole().name(),
                                            p.selectedStatus().name(),
                                            null,
                                            false,
                                            false),
                                    expiry,
                                    report.bindingHash(),
                                    report);
                        });
        registry = new SimpleMeterRegistry();
        metrics = new ExportJobMetrics(jdbc, registry);
        jobs =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM auth_export_jobs");
        jdbc.update("DELETE FROM auth_outbox WHERE event_type='export.state.changed'");
        jdbc.update("DELETE FROM audit_log WHERE action='registry.export'");
        long id = jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
        long grant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",
                        Long.class,
                        id);
        long rv = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?", Long.class, id);
        UUID session = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method) VALUES (?,?,?,1,?,NOW()+interval '1 day',NOW(),NOW(),'PASSWORD')",
                session,
                id,
                grant,
                UUID.randomUUID());
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,assigned=true,mode='FULL',permissions='{}' WHERE user_id=?",
                id);
        rv = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=?", Long.class, id);
        actor =
                new SessionPrincipal(
                        id, session, 1, rv, AuthRole.ADMIN, RoleStatus.ACTIVE, null, false, false);
    }

    private IssueReportDownloadTicketRequest users() {
        return new IssueReportDownloadTicketRequest(
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
                        "synthetic", null, null, null, null, ReportDownloadFormat.HTML),
                null);
    }

    @Test
    void groupAttendanceRequiresOnlyLiveDashboardAndRevocationProtectsEveryStoredSurface() {
        var report =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.ADMIN_GROUP_ATTENDANCE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.AdminGroupAttendanceParameters(
                                "1", "", "GROUP_NAME", "ASC", ReportDownloadFormat.HTML));
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY['GROUPS_VIEW']"
                        + " WHERE user_id=?",
                actor.userId());
        actor =
                new SessionPrincipal(
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        jdbc.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?",
                                Long.class,
                                actor.userId()),
                        actor.selectedRole(),
                        actor.selectedStatus(),
                        actor.groupId(),
                        actor.headman(),
                        actor.readOnly());
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        ignored ->
                                                jobs.enqueue(
                                                        actor,
                                                        new Create(UUID.randomUUID(), report))))
                .isInstanceOfSatisfying(
                        AdminAccessException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isZero();
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,permissions=ARRAY['DASHBOARD_VIEW'] WHERE"
                        + " user_id=?",
                actor.userId());
        actor =
                new SessionPrincipal(
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        jdbc.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?",
                                Long.class,
                                actor.userId()),
                        actor.selectedRole(),
                        actor.selectedStatus(),
                        actor.groupId(),
                        actor.headman(),
                        actor.readOnly());
        UUID request = UUID.randomUUID();
        Status queued = tx.execute(ignored -> jobs.enqueue(actor, new Create(request, report)));
        assertThat(tx.execute(ignored -> jobs.enqueue(actor, new Create(request, report))).jobId())
                .isEqualTo(queued.jobId());
        Claim claimed = claim();
        assertThat(claimed.authority().report().kind())
                .isEqualTo(ReportDownloadKind.ADMIN_GROUP_ATTENDANCE);
        var completed =
                new Complete(
                        claimed.leaseId(),
                        "<html>group totals only</html>".getBytes(StandardCharsets.UTF_8),
                        "text/html",
                        2,
                        false);
        tx.execute(ignored -> jobs.complete(claimed.jobId(), completed));
        tx.execute(ignored -> jobs.complete(claimed.jobId(), completed));
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.READY);
        assertThat(jobs.file(actor, queued.jobId()).content()).isEqualTo(completed.content());
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " target_type='ADMIN_GROUP_ATTENDANCE'"))
                .isEqualTo(1);
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,permissions=ARRAY['GROUPS_VIEW'] WHERE user_id=?",
                actor.userId());
        for (Runnable operation :
                List.<Runnable>of(
                        () -> jobs.status(actor, queued.jobId()),
                        () -> jobs.file(actor, queued.jobId()),
                        () -> jobs.ticket(actor, queued.jobId()),
                        () ->
                                tx.execute(
                                        ignored ->
                                                jobs.enqueue(
                                                        actor,
                                                        new Create(UUID.randomUUID(), report))))) {
            assertThatThrownBy(operation::run)
                    .isInstanceOfSatisfying(
                            AdminAccessException.class,
                            error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
        }
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND"
                                        + " target_type='ADMIN_GROUP_ATTENDANCE'"))
                .isEqualTo(1);
    }

    private Status enqueue() {
        return tx.execute(ignored -> jobs.enqueue(actor, new Create(UUID.randomUUID(), users())));
    }

    private Claim claim() {
        return tx.execute(ignored -> jobs.claim().orElseThrow());
    }

    private Complete complete(Claim claim, boolean full) {
        return new Complete(
                claim.leaseId(),
                "<html>synthetic registry</html>".getBytes(StandardCharsets.UTF_8),
                "text/html",
                3,
                full);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void durableQueueSurvivesNewWorkerAndAbandonedLeaseWithoutPartialFileOrDuplicateAudit() {
        UUID request = UUID.randomUUID();
        Status queued = tx.execute(ignored -> jobs.enqueue(actor, new Create(request, users())));
        assertThat(queued.state()).isEqualTo(State.QUEUED);
        Status duplicate = tx.execute(ignored -> jobs.enqueue(actor, new Create(request, users())));
        assertThat(duplicate.jobId()).isEqualTo(queued.jobId());
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isEqualTo(1);
        Claim lost = claim();
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.PREPARING);
        assertThatThrownBy(() -> jobs.file(actor, queued.jobId()))
                .isInstanceOf(ResponseStatusException.class);
        jdbc.update(
                "UPDATE auth_export_jobs SET lease_until=NOW()-interval '1 second' WHERE job_id=?",
                queued.jobId());
        jobs =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
        Claim recovered = claim();
        assertThat(recovered.jobId()).isEqualTo(lost.jobId());
        assertThat(recovered.leaseId()).isNotEqualTo(lost.leaseId());
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        ignored ->
                                                jobs.complete(lost.jobId(), complete(lost, true))))
                .isInstanceOf(ResponseStatusException.class);
        tx.execute(ignored -> jobs.complete(recovered.jobId(), complete(recovered, true)));
        tx.execute(ignored -> jobs.complete(recovered.jobId(), complete(recovered, true)));
        assertThat(jobs.file(actor, queued.jobId()).content())
                .isEqualTo(complete(recovered, true).content());
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'"))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_outbox WHERE payload->'payload'->>'state'='ready'"))
                .isEqualTo(1);
        String identity =
                jdbc.queryForObject(
                        "SELECT identity::text FROM auth_export_jobs WHERE job_id=?",
                        String.class,
                        queued.jobId());
        assertThat(identity).doesNotContain("synthetic-internal-token", "password", "Bearer");
        String audit =
                jdbc.queryForObject(
                        "SELECT extras::text FROM audit_log WHERE action='registry.export'",
                        String.class);
        assertThat(audit)
                .contains("rowCount", "3", "jobId")
                .doesNotContain("synthetic registry", "Telegram", "password");
    }

    @Test
    void
            fullFileRemainsProtectedAfterDowngradeEvenWithOrdinaryViewGrantAndForeignSessionIsHidden() {
        Status queued = enqueue();
        Claim claim = claim();
        tx.execute(ignored -> jobs.complete(claim.jobId(), complete(claim, true)));
        assertThat(jobs.file(actor, queued.jobId()).content()).isNotEmpty();
        var foreign =
                new SessionPrincipal(
                        actor.userId(),
                        UUID.randomUUID(),
                        1,
                        actor.rolesVersion(),
                        AuthRole.ADMIN,
                        RoleStatus.ACTIVE,
                        null,
                        false,
                        false);
        assertThatThrownBy(() -> jobs.status(foreign, queued.jobId()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY['USERS_VIEW'] WHERE user_id=?",
                actor.userId());
        assertThatThrownBy(() -> jobs.status(actor, queued.jobId()))
                .isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor, queued.jobId()))
                .isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.ticket(actor, queued.jobId()))
                .isInstanceOf(AdminAccessException.class);
    }

    @Test
    void ordinaryLimitedGrantWorksAndRevokeDeniesEveryLaterReadAndCompletingClaim() {
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY['USERS_VIEW'] WHERE user_id=?",
                actor.userId());
        actor =
                new SessionPrincipal(
                        actor.userId(),
                        actor.sessionId(),
                        actor.sessionVersion(),
                        jdbc.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?",
                                Long.class,
                                actor.userId()),
                        AuthRole.ADMIN,
                        RoleStatus.ACTIVE,
                        null,
                        false,
                        false);
        Status queued = enqueue();
        Claim claim = claim();
        tx.execute(ignored -> jobs.complete(claim.jobId(), complete(claim, false)));
        assertThat(jobs.file(actor, queued.jobId()).content()).isNotEmpty();
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,permissions=ARRAY['GROUPS_VIEW'] WHERE user_id=?",
                actor.userId());
        assertThatThrownBy(() -> jobs.status(actor, queued.jobId()))
                .isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor, queued.jobId()))
                .isInstanceOf(AdminAccessException.class);
        assertThatThrownBy(() -> enqueue()).isInstanceOf(AdminAccessException.class);
    }

    @Test
    void failedOutboxInsertRollsBackReadyFileAndAuditThenRetryCommitsExactlyOnce() {
        Status queued = enqueue();
        Claim claim = claim();
        jdbc.execute(
                "ALTER TABLE auth_outbox ADD CONSTRAINT fixture_ready_failure CHECK (payload->'payload'->>'state' IS DISTINCT FROM 'ready')");
        try {
            assertThatThrownBy(
                            () ->
                                    tx.execute(
                                            ignored ->
                                                    jobs.complete(
                                                            claim.jobId(), complete(claim, true))))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        } finally {
            jdbc.execute("ALTER TABLE auth_outbox DROP CONSTRAINT fixture_ready_failure");
        }
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.PREPARING);
        assertThat(count("SELECT count(*) FROM auth_export_jobs WHERE content IS NOT NULL"))
                .isZero();
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'")).isZero();
        tx.execute(ignored -> jobs.complete(claim.jobId(), complete(claim, true)));
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.READY);
        assertThat(count("SELECT count(*) FROM audit_log WHERE action='registry.export'"))
                .isEqualTo(1);
    }

    @Test
    void threeGenerationFailuresTerminateAndWireAndDatabaseRejectUnknownOrOrdinalState()
            throws Exception {
        Status queued = enqueue();
        for (int i = 0; i < 3; i++) {
            Claim claim = claim();
            tx.executeWithoutResult(
                    ignored ->
                            jobs.fail(
                                    claim.jobId(), new Fail(claim.leaseId(), "GENERATION_FAILED")));
            if (i < 2) {
                assertThat(tx.<Optional<Claim>>execute(ignored -> jobs.claim())).isEmpty();
                Double delay =
                        jdbc.queryForObject(
                                "SELECT EXTRACT(EPOCH FROM lease_until-NOW()) FROM auth_export_jobs",
                                Double.class);
                assertThat(delay).isBetween(i == 0 ? 3.0 : 8.0, i == 0 ? 6.0 : 11.0);
                jdbc.update(
                        "UPDATE auth_export_jobs SET lease_until=NOW()-interval '1 second' WHERE job_id=?",
                        queued.jobId());
            }
        }
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.ERROR);
        assertThat(count("SELECT attempts FROM auth_export_jobs")).isEqualTo(3);
        assertThat(mapper.writeValueAsString(State.PREPARING)).isEqualTo("\"preparing\"");
        assertThat(mapper.readValue("\"ready\"", State.class)).isEqualTo(State.READY);
        assertThatThrownBy(() -> mapper.readValue("\"unknown\"", State.class))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE auth_export_jobs SET state='1'"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jobs.file(actor, queued.jobId()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void retryDueSurvivesServiceRestartAndExpiredLeaseCannotCommitOverTheNewAttempt() {
        Status queued = enqueue();
        Claim first = claim();
        tx.executeWithoutResult(
                ignored ->
                        jobs.fail(first.jobId(), new Fail(first.leaseId(), "GENERATION_FAILED")));
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.QUEUED);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_export_jobs WHERE state='queued' AND lease_id IS NULL AND lease_until>NOW()+interval '3 seconds' AND lease_until<=NOW()+interval '6 seconds'"))
                .isEqualTo(1);
        jobs =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
        assertThat(tx.<Optional<Claim>>execute(ignored -> jobs.claim())).isEmpty();
        jdbc.update(
                "UPDATE auth_export_jobs SET lease_until=NOW()-interval '1 second' WHERE job_id=?",
                queued.jobId());
        Claim next = claim();
        assertThat(next.jobId()).isEqualTo(first.jobId());
        assertThat(next.leaseId()).isNotEqualTo(first.leaseId());
        assertThatThrownBy(
                        () ->
                                tx.execute(
                                        ignored ->
                                                jobs.complete(
                                                        first.jobId(), complete(first, false))))
                .isInstanceOf(ResponseStatusException.class);
        tx.execute(ignored -> jobs.complete(next.jobId(), complete(next, false)));
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.READY);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_outbox WHERE payload->'payload'->>'state'='ready'"))
                .isEqualTo(1);
    }

    @Test
    void sevenConcurrentClaimsAcrossServiceInstancesHaveOnlyTwoActiveLeases() throws Exception {
        for (int i = 0; i < 7; i++) enqueue();
        var second =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
        var barrier = new CyclicBarrier(7);
        var pool = Executors.newFixedThreadPool(7);
        try {
            List<Future<Optional<Claim>>> futures = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                var service = i % 2 == 0 ? jobs : second;
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await(10, TimeUnit.SECONDS);
                                    return tx.execute(ignored -> service.claim());
                                }));
            }
            List<Claim> accepted = new ArrayList<>();
            for (var future : futures) future.get(10, TimeUnit.SECONDS).ifPresent(accepted::add);
            assertThat(accepted).hasSize(2);
            assertThat(
                            count(
                                    "SELECT count(*) FROM auth_export_jobs WHERE state='preparing' AND lease_until>NOW()"))
                    .isEqualTo(2);
            assertThat(count("SELECT count(*) FROM auth_export_jobs WHERE state='queued'"))
                    .isEqualTo(5);
            Claim completed = accepted.getFirst();
            tx.execute(ignored -> jobs.complete(completed.jobId(), complete(completed, false)));
            assertThat(tx.<Optional<Claim>>execute(ignored -> second.claim())).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void idleExpiryCleanupReclaimsOnlyExpiredFilesAndNeverDeletesLiveQueuedWork() {
        Status expired = enqueue();
        Claim ready = claim();
        tx.execute(ignored -> jobs.complete(ready.jobId(), complete(ready, false)));
        Status live = enqueue();
        jdbc.update(
                "UPDATE auth_export_jobs SET expires_at=NOW()-interval '1 second' WHERE job_id=?",
                expired.jobId());
        tx.executeWithoutResult(ignored -> jobs.cleanExpired());
        assertThat(count("SELECT count(*) FROM auth_export_jobs WHERE content IS NOT NULL"))
                .isZero();
        assertThat(jobs.status(actor, live.jobId()).state()).isEqualTo(State.QUEUED);
        assertThatThrownBy(() -> jobs.status(actor, expired.jobId()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void retryThatCannotFitTheRetentionWindowTerminatesInsteadOfBecomingInvisibleQueuedWork()
            throws Exception {
        Status queued = enqueue();
        Claim first = claim();
        // Persist the real near-expiry identity as well as the row deadline.
        Instant nearExpiry = Instant.now().plusSeconds(4);
        jdbc.update(
                "UPDATE auth_export_jobs SET identity=jsonb_set(identity,'{expiresAt}',?::jsonb),expires_at=? WHERE job_id=?",
                mapper.writeValueAsString(nearExpiry),
                nearExpiry.atOffset(java.time.ZoneOffset.UTC),
                queued.jobId());
        tx.executeWithoutResult(
                ignored ->
                        jobs.fail(first.jobId(), new Fail(first.leaseId(), "GENERATION_FAILED")));
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_export_jobs WHERE state='error' AND error_code='GENERATION_FAILED'"))
                .isEqualTo(1);
    }

    @Test
    void acceptedEighteenWeekSelectorAndCompletedBytesRemainDurableAcrossServiceRestart() {
        // Ticket authority is stubbed here; this fixture proves durable selection and byte storage.
        var weeks =
                java.util.stream.IntStream.range(0, 18)
                        .mapToObj(week -> LocalDate.of(2026, 2, 2).plusWeeks(week))
                        .toList();
        var report =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_WEEKLY_SELECTED,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanWeeklySelectedParameters(
                                weeks, ReportDownloadFormat.DOCX),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        UUID request = UUID.randomUUID();
        Status queued = tx.execute(ignored -> jobs.enqueue(actor, new Create(request, report)));
        jobs =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
        assertThat(tx.execute(ignored -> jobs.enqueue(actor, new Create(request, report))).jobId())
                .isEqualTo(queued.jobId());
        Claim claimed = claim();
        assertThat(claimed.authority().report().headmanWeeklySelected().weekStarts())
                .containsExactlyElementsOf(weeks);
        byte[] bytes = "synthetic document fixture".getBytes(StandardCharsets.UTF_8);
        var result = new Complete(claimed.leaseId(), bytes, report.expectedMediaType(), 18, false);
        tx.execute(ignored -> jobs.complete(claimed.jobId(), result));
        jobs =
                new ExportJobService(
                        jdbc, mapper, new ExportReportAuthority(tickets, jdbc), tickets, metrics);
        assertThat(jobs.file(actor, queued.jobId()).content()).isEqualTo(bytes);
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.READY);
    }

    @Test
    void committedMetricsIgnoreRollbackAndCountOneFinalEffectForAnIdempotentCompletion() {
        var accepted =
                registry.get("rct.export.jobs.accepted").tag("kind", "ADMIN_USERS").counter();
        var success =
                registry.get("rct.export.jobs.terminal")
                        .tags("kind", "ADMIN_USERS", "outcome", "success", "reason", "none")
                        .counter();
        var retry = registry.get("rct.export.jobs.retries").tag("kind", "ADMIN_USERS").counter();
        var duration =
                registry.get("rct.export.jobs.duration")
                        .tags("kind", "ADMIN_USERS", "outcome", "success")
                        .timer();
        double acceptedBefore = accepted.count(),
                successBefore = success.count(),
                retryBefore = retry.count();
        long durationsBefore = duration.count();
        tx.executeWithoutResult(
                status -> {
                    jobs.enqueue(actor, new Create(UUID.randomUUID(), users()));
                    status.setRollbackOnly();
                });
        assertThat(accepted.count()).isEqualTo(acceptedBefore);
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isZero();
        Status queued = enqueue();
        assertThat(accepted.count()).isEqualTo(acceptedBefore + 1);
        Claim claimed = claim();
        tx.executeWithoutResult(
                status -> {
                    jobs.complete(claimed.jobId(), complete(claimed, false));
                    status.setRollbackOnly();
                });
        assertThat(success.count()).isEqualTo(successBefore);
        assertThat(duration.count()).isEqualTo(durationsBefore);
        assertThat(jobs.status(actor, queued.jobId()).state()).isEqualTo(State.PREPARING);
        tx.execute(ignored -> jobs.complete(claimed.jobId(), complete(claimed, false)));
        tx.execute(ignored -> jobs.complete(claimed.jobId(), complete(claimed, false)));
        assertThat(success.count()).isEqualTo(successBefore + 1);
        assertThat(duration.count()).isEqualTo(durationsBefore + 1);

        enqueue();
        Claim failing = claim();
        tx.executeWithoutResult(
                status -> {
                    jobs.fail(failing.jobId(), new Fail(failing.leaseId(), "GENERATION_FAILED"));
                    status.setRollbackOnly();
                });
        assertThat(retry.count()).isEqualTo(retryBefore);
        assertThat(jobs.status(actor, failing.jobId()).state()).isEqualTo(State.PREPARING);
        tx.executeWithoutResult(
                ignored ->
                        jobs.fail(
                                failing.jobId(), new Fail(failing.leaseId(), "GENERATION_FAILED")));
        assertThat(retry.count()).isEqualTo(retryBefore + 1);
        assertThat(jobs.status(actor, failing.jobId()).state()).isEqualTo(State.QUEUED);
    }

    @Test
    void expiryCountsAsFailureOnlyAfterDeleteCommitAndExpectedDenialIsSeparate() {
        var expired =
                registry.get("rct.export.jobs.terminal")
                        .tags("kind", "ADMIN_USERS", "outcome", "failure", "reason", "expired")
                        .counter();
        var denied =
                registry.get("rct.export.jobs.terminal")
                        .tags(
                                "kind",
                                "ADMIN_USERS",
                                "outcome",
                                "denied",
                                "reason",
                                "authority_denied")
                        .counter();
        double expiredBefore = expired.count(), deniedBefore = denied.count();
        Status waiting = enqueue();
        jdbc.update(
                "UPDATE auth_export_jobs SET expires_at=NOW()-interval '1 second' WHERE job_id=?",
                waiting.jobId());
        tx.executeWithoutResult(
                status -> {
                    jobs.cleanExpired();
                    status.setRollbackOnly();
                });
        assertThat(expired.count()).isEqualTo(expiredBefore);
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isEqualTo(1);
        tx.executeWithoutResult(ignored -> jobs.cleanExpired());
        assertThat(expired.count()).isEqualTo(expiredBefore + 1);
        assertThat(count("SELECT count(*) FROM auth_export_jobs")).isZero();
        enqueue();
        Claim claim = claim();
        tx.executeWithoutResult(
                status -> {
                    jobs.fail(claim.jobId(), new Fail(claim.leaseId(), "AUTHORITY_DENIED"));
                    status.setRollbackOnly();
                });
        assertThat(denied.count()).isEqualTo(deniedBefore);
        tx.executeWithoutResult(
                ignored -> jobs.fail(claim.jobId(), new Fail(claim.leaseId(), "AUTHORITY_DENIED")));
        tx.executeWithoutResult(
                ignored -> jobs.fail(claim.jobId(), new Fail(claim.leaseId(), "AUTHORITY_DENIED")));
        assertThat(denied.count()).isEqualTo(deniedBefore + 1);
    }

    @Test
    void scheduledSnapshotUsesOnlyFiniteKindsAndSeparatesBackoffFromDueWork() {
        Status queued = enqueue();
        Claim claimed = claim();
        tx.executeWithoutResult(
                ignored ->
                        jobs.fail(
                                claimed.jobId(), new Fail(claimed.leaseId(), "GENERATION_FAILED")));
        metrics.refresh();
        assertThat(
                        registry.get("rct.export.jobs.waiting")
                                .tag("kind", "ADMIN_USERS")
                                .gauge()
                                .value())
                .isEqualTo(1);
        assertThat(registry.get("rct.export.jobs.due").tag("kind", "ADMIN_USERS").gauge().value())
                .isZero();
        assertThat(registry.get("rct.export.jobs.snapshot.timestamp").gauge().value()).isPositive();
        String marker = "synthetic-untrusted-kind-never-a-label";
        jdbc.update(
                "UPDATE auth_export_jobs SET identity=jsonb_set(identity,'{report,kind}',to_jsonb(?::text)),lease_until=NULL WHERE job_id=?",
                marker,
                queued.jobId());
        metrics.refresh();
        assertThat(
                        registry.get("rct.export.jobs.queue")
                                .tags("kind", "unknown", "state", "queued")
                                .gauge()
                                .value())
                .isEqualTo(1);
        assertThat(registry.get("rct.export.jobs.due").tag("kind", "unknown").gauge().value())
                .isEqualTo(1);
        assertThat(
                        registry.getMeters().stream()
                                .flatMap(meter -> meter.getId().getTags().stream())
                                .anyMatch(tag -> tag.getValue().contains(marker)))
                .isFalse();
    }
}
