package ru.rutcampustrack.academic.exportjobs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual owner SQL, advisory locks and CAS; no shared Auth tables or remote calls under locks. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcademicExportJobsIT {
    private final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("academic_jobs")
                    .withUsername("academic_jobs")
                    .withPassword("synthetic_jobs_fixture")
                    .withLabel("rct.validation.owner", "s19_jobs");
    private JdbcTemplate jdbc;
    private ExportJobRepository jobs;
    private LocalReportAuthority local;
    private SimpleMeterRegistry registry;
    private ExportJobMetrics metrics;
    private ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority assistants;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final InternalJwtClaims actor =
            new InternalJwtClaims(
                    101, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false);

    @BeforeAll
    void start() throws Exception {
        postgres.start();
        var ds =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE academic_profiles(id bigint PRIMARY KEY,academic_revision bigint NOT"
                    + " NULL,applied_auth_roles_version bigint NOT NULL,archived_at timestamptz)");
        jdbc.execute(
                "CREATE TABLE academic_outbox(event_type text NOT NULL,payload jsonb NOT NULL)");
        jdbc.execute(
                "CREATE TABLE audit_log(user_id bigint,action text,target_type text,extras"
                        + " jsonb,succeeded boolean,created_at timestamptz)");
        jdbc.execute("CREATE TABLE groups(id bigint PRIMARY KEY,is_active boolean NOT NULL)");
        jdbc.execute(
                "CREATE TABLE academic_role_eligibility(user_id bigint,role text,status"
                        + " text,group_id bigint)");
        jdbc.execute(
                "CREATE TABLE semesters(id bigint PRIMARY KEY,is_active boolean NOT NULL,date_from"
                        + " date NOT NULL,date_to date NOT NULL,deletion_phase text)");
        jdbc.execute(
                "CREATE TABLE assignments(id bigint PRIMARY KEY,teacher_id bigint,semester_id"
                    + " bigint,group_id bigint,subject_id bigint,lifecycle_state text,valid_from"
                    + " date,valid_until_exclusive date)");
        jdbc.execute(
                "CREATE TABLE academic_identity_operations(operation_id uuid PRIMARY KEY,phase"
                        + " text)");
        jdbc.execute(
                "CREATE TABLE academic_identity_operation_targets(operation_id uuid,user_id"
                        + " bigint,fenced boolean)");
        jdbc.execute(
                "CREATE TABLE academic_identity_operation_groups(operation_id uuid,group_id"
                        + " bigint,fenced boolean)");
        jdbc.execute(
                "CREATE TABLE academic_group_availability_guards(group_id bigint PRIMARY KEY,"
                        + "operation_id uuid NOT NULL,original_group_revision bigint NOT NULL)");
        jdbc.execute(
                "CREATE TABLE academic_group_availability_headmen(user_id bigint PRIMARY KEY,"
                        + "operation_id uuid NOT NULL)");
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/academic-migration/V2__academic_export_jobs.sql"));
        }
        assistants = mock(ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority.class);
        var authority =
                new LocalReportAuthority(
                        jdbc,
                        assistants,
                        new ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence(
                                jdbc));
        var localFactory = new ProxyFactory(authority);
        localFactory.setProxyTargetClass(true);
        localFactory.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(ds),
                        new AnnotationTransactionAttributeSource()));
        local = (LocalReportAuthority) localFactory.getProxy();
        registry = new SimpleMeterRegistry();
        metrics = new ExportJobMetrics(jdbc, registry);
        var target = new ExportJobRepository(jdbc, mapper, metrics, local);
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                new TransactionInterceptor(
                        new DataSourceTransactionManager(ds),
                        new AnnotationTransactionAttributeSource()));
        jobs = (ExportJobRepository) factory.getProxy();
    }

    @AfterAll
    void stop() {
        postgres.stop();
    }

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM academic_export_jobs");
        jdbc.update("DELETE FROM academic_outbox");
        jdbc.update("DELETE FROM audit_log");
        jdbc.update("DELETE FROM academic_profiles");
        jdbc.update("DELETE FROM groups");
        jdbc.update("DELETE FROM semesters");
        jdbc.update("DELETE FROM academic_role_eligibility");
        jdbc.update("DELETE FROM assignments");
        jdbc.update("DELETE FROM academic_identity_operation_targets");
        jdbc.update("DELETE FROM academic_identity_operation_groups");
        jdbc.update("DELETE FROM academic_identity_operations");
        jdbc.update("DELETE FROM academic_group_availability_guards");
        jdbc.update("DELETE FROM academic_group_availability_headmen");
        reset(assistants);
        jdbc.update("INSERT INTO academic_profiles VALUES (101,1,1,NULL)");
    }

    private IssueReportDownloadTicketRequest selector(String search) {
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
                        search, null, null, null, null, ReportDownloadFormat.HTML),
                null);
    }

    private StoredReportIdentity plan() {
        return local.plan(actor, selector("synthetic"), Instant.now().plusSeconds(599));
    }

    private ExportJobRepository.Job enqueue() {
        return jobs.enqueue(UUID.randomUUID(), plan(), true);
    }

    private ExportJobRepository.Job claim(ExportJobRepository.Job job) {
        return jobs.claim(job, true).orElseThrow();
    }

    private void complete(ExportJobRepository.Job job) {
        jobs.complete(
                job.id(),
                job.leaseId(),
                "<html>synthetic</html>".getBytes(StandardCharsets.UTF_8),
                "text/html",
                1,
                true,
                job.identity());
    }

    @Test
    void lostAcceptedAndCompletionAcknowledgementsResolveSameJobAndOneAudit() {
        UUID request = UUID.randomUUID();
        var identity = plan();
        var first = jobs.enqueue(request, identity, true);
        var same = jobs.enqueue(request, identity, true);
        assertThat(same.id()).isEqualTo(first.id());
        assertThatThrownBy(
                        () ->
                                jobs.enqueue(
                                        request,
                                        local.plan(
                                                actor, selector("different"), identity.expiresAt()),
                                        true))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        var running = claim(first);
        complete(running);
        complete(running);
        assertThat(jobs.load(first.id()).state()).isEqualTo("ready");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM academic_outbox WHERE"
                                        + " payload->'payload'->>'state'='ready'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT identity::text FROM academic_export_jobs", String.class))
                .doesNotContain("internalToken", "Bearer ", "signed-token", "password");
    }

    @Test
    void simultaneousClaimsAcrossWorkersNeverExceedTwoGlobalLeases() throws Exception {
        var candidates = List.of(enqueue(), enqueue(), enqueue(), enqueue());
        var executor = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Boolean>>();
            for (var job : candidates)
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return jobs.claim(job, true).isPresent();
                                }));
            start.countDown();
            int count = 0;
            for (var result : futures) if (result.get(10, TimeUnit.SECONDS)) count++;
            assertThat(count).isEqualTo(2);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM academic_export_jobs WHERE"
                                            + " state='preparing' AND lease_until>NOW()",
                                    Integer.class))
                    .isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void retryBackoffIsFiveThenTenAndThirdAbandonedLeaseTerminatesSameJob() {
        var first = claim(enqueue());
        jobs.fail(first.id(), first.leaseId(), "GENERATION_FAILED");
        double delay =
                jdbc.queryForObject(
                        "SELECT EXTRACT(EPOCH FROM lease_until-NOW()) FROM academic_export_jobs",
                        Double.class);
        assertThat(delay).isBetween(4.0, 5.1);
        assertThat(jobs.claim(jobs.load(first.id()), true)).isEmpty();
        jdbc.update("UPDATE academic_export_jobs SET lease_until=NOW()-interval '1 second'");
        var second = claim(jobs.load(first.id()));
        jobs.fail(second.id(), second.leaseId(), "GENERATION_FAILED");
        delay =
                jdbc.queryForObject(
                        "SELECT EXTRACT(EPOCH FROM lease_until-NOW()) FROM academic_export_jobs",
                        Double.class);
        assertThat(delay).isBetween(9.0, 10.1);
        jdbc.update("UPDATE academic_export_jobs SET lease_until=NOW()-interval '1 second'");
        var third = claim(jobs.load(first.id()));
        assertThat(third.attempts()).isEqualTo(3);
        jdbc.update("UPDATE academic_export_jobs SET lease_until=NOW()-interval '1 second'");
        jobs.recoverAbandoned();
        assertThat(jobs.load(first.id()).state()).isEqualTo("error");
        assertThat(jobs.load(first.id()).errorCode()).isEqualTo("GENERATION_FAILED");
        assertThatThrownBy(() -> complete(first)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staleLeaseCannotPublishAfterCrashReclaimAndLocalRevocationBlocksReady() {
        var first = claim(enqueue());
        jdbc.update("UPDATE academic_export_jobs SET lease_until=NOW()-interval '1 second'");
        var replacement = claim(jobs.load(first.id()));
        assertThat(replacement.id()).isEqualTo(first.id());
        assertThat(replacement.leaseId()).isNotEqualTo(first.leaseId());
        assertThatThrownBy(() -> complete(first)).isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE academic_profiles SET academic_revision=2 WHERE id=101");
        assertThatThrownBy(() -> complete(replacement))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content IS NULL FROM academic_export_jobs", Boolean.class))
                .isTrue();
    }

    @Test
    void personalBoundAndExpiryReclamationUseOnlyAcademicQueue() {
        for (int i = 0; i < 10; i++) enqueue();
        assertThatThrownBy(this::enqueue)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        jdbc.update(
                "UPDATE academic_export_jobs SET created_at=NOW()-interval '11"
                        + " minutes',expires_at=NOW()-interval '1 minute'");
        jobs.cleanExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_export_jobs", Integer.class))
                .isZero();
        assertThat(enqueue().state()).isEqualTo("queued");
    }

    @Test
    void outboxFailureRollsBackArtifactAuditAndCommittedMetricThenSameLeaseRetries() {
        var job = claim(enqueue());
        var counter =
                registry.counter(
                        "rct.export.jobs.terminal",
                        "kind",
                        "ADMIN_USERS",
                        "outcome",
                        "success",
                        "reason",
                        "none");
        double before = counter.count();
        jdbc.execute(
                "CREATE FUNCTION reject_ready_export() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                    + " IF NEW.payload->'payload'->>'state'='ready' THEN RAISE EXCEPTION 'synthetic"
                    + " outbox unavailable'; END IF; RETURN NEW; END $$");
        jdbc.execute(
                "CREATE TRIGGER reject_ready_export BEFORE INSERT ON academic_outbox FOR EACH ROW"
                        + " EXECUTE FUNCTION reject_ready_export()");
        try {
            assertThatThrownBy(() -> complete(job))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(jobs.load(job.id()).state()).isEqualTo("preparing");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT content IS NULL FROM academic_export_jobs",
                                    Boolean.class))
                    .isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class))
                    .isZero();
            assertThat(counter.count()).isEqualTo(before);
        } finally {
            jdbc.execute("DROP TRIGGER reject_ready_export ON academic_outbox");
            jdbc.execute("DROP FUNCTION reject_ready_export()");
        }
        complete(job);
        complete(job);
        assertThat(counter.count()).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void globalBacklogIsBoundedAcrossDifferentUsersAndSessions() {
        for (int i = 0; i < 256; i++) {
            long user = 1000L + i;
            jdbc.update("INSERT INTO academic_profiles VALUES (?,1,1,NULL)", user);
            var principal =
                    new InternalJwtClaims(
                            user, UUID.randomUUID(), 1, 1, "ADMIN", "ACTIVE", null, false, false);
            jobs.enqueue(
                    UUID.randomUUID(),
                    local.plan(principal, selector("synthetic"), Instant.now().plusSeconds(599)),
                    false);
        }
        assertThatThrownBy(this::enqueue)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_export_jobs", Integer.class))
                .isEqualTo(256);
    }

    @Test
    void currentGroupArchiveAndAmbiguousTeacherSemesterDenyLocalReportAuthority() {
        var headman =
                new InternalJwtClaims(
                        101, actor.sessionId(), 1, 1, "HEADMAN", "ACTIVE", 501L, true, false);
        jdbc.update("INSERT INTO groups VALUES (501,TRUE)");
        jdbc.update("INSERT INTO academic_role_eligibility VALUES (101,'HEADMAN','ACTIVE',501)");
        var roster =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                ReportDownloadFormat.HTML));
        var identity = local.plan(headman, roster, Instant.now().plusSeconds(599));
        jdbc.update("UPDATE groups SET is_active=FALSE WHERE id=501");
        assertThatThrownBy(() -> local.require(identity, true))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        jdbc.update(
                "INSERT INTO semesters VALUES"
                    + " (1,TRUE,current_date-1,current_date+1,NULL),(2,TRUE,current_date-1,current_date+1,NULL)");
        assertThatThrownBy(() -> local.teacherScope(101))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e ->
                                assertThat(e.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void delegatedStatsPermissionIsFreshAndNeverAuthorizesGroupComposition() {
        var helper =
                new InternalJwtClaims(
                        101, actor.sessionId(), 1, 1, "STUDENT", "ACTIVE", 501L, false, false);
        jdbc.update("INSERT INTO groups VALUES (501,TRUE)");
        jdbc.update("INSERT INTO academic_role_eligibility VALUES (101,'STUDENT','ACTIVE',501)");
        when(assistants.allows(helper, 501L, "VIEW_STATS")).thenReturn(true);
        var stats =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                                null, null, null, null, ReportDownloadFormat.HTML));
        // Missing active semester is valid admission; the actual exporter retains its empty/error
        // behavior.
        var identity = local.plan(helper, stats, Instant.now().plusSeconds(599));
        assertThat(identity.semesterId()).isNull();
        var composition =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                ReportDownloadFormat.HTML));
        assertThatThrownBy(() -> local.plan(helper, composition, Instant.now().plusSeconds(599)))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        when(assistants.allows(helper, 501L, "VIEW_STATS")).thenReturn(false);
        assertThatThrownBy(() -> local.require(identity, true))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void absentActiveSemesterIsFrozenAndCannotSilentlyChangeTheQueuedSelector() {
        var headman =
                new InternalJwtClaims(
                        101, actor.sessionId(), 1, 1, "HEADMAN", "ACTIVE", 501L, true, false);
        jdbc.update("INSERT INTO groups VALUES (501,TRUE)");
        jdbc.update("INSERT INTO academic_role_eligibility VALUES (101,'HEADMAN','ACTIVE',501)");
        when(assistants.allows(headman, 501L, "VIEW_STATS")).thenReturn(true);
        var trend =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_STATS_TREND,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                                IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.SEMESTER,
                                null,
                                null,
                                null,
                                ReportDownloadFormat.HTML));
        var identity = local.plan(headman, trend, Instant.now().plusSeconds(599));
        assertThat(identity.semesterId()).isNull();
        local.require(identity, false);
        jdbc.update("INSERT INTO semesters VALUES (1,TRUE,current_date-1,current_date+1,NULL)");
        assertThatThrownBy(() -> local.require(identity, false))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void teacherReadyArtifactRequiresEveryCapturedCurrentAssignmentAfterScopeShrinks() {
        var teacher =
                new InternalJwtClaims(
                        101, actor.sessionId(), 1, 1, "TEACHER", "ACTIVE", null, false, false);
        jdbc.update("INSERT INTO academic_role_eligibility VALUES (101,'TEACHER','ACTIVE',NULL)");
        jdbc.update("INSERT INTO semesters VALUES (1,TRUE,current_date-2,current_date+2,NULL)");
        jdbc.update(
                "INSERT INTO assignments VALUES (1,101,1,501,601,'ACTIVE',current_date-1,NULL),"
                        + "(2,101,1,502,602,'ACTIVE',current_date-1,NULL)");
        var selector =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.TEACHER_STATS,
                        null,
                        new IssueReportDownloadTicketRequest.TeacherStatsParameters(
                                1L,
                                "groups",
                                null,
                                null,
                                null,
                                null,
                                null,
                                ReportDownloadFormat.HTML),
                        null,
                        null,
                        null);
        var identity = local.plan(teacher, selector, Instant.now().plusSeconds(599));
        var captured = local.capture(identity, local.teacherScope(101).sha256());
        local.require(captured, true);
        jdbc.update("UPDATE assignments SET lifecycle_state='REVOKED' WHERE id=2");
        // Ordinary current teacher admission still succeeds on the remaining pair.
        local.require(identity, false);
        assertThatThrownBy(() -> local.require(captured, true))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> local.capture(identity, captured.teacherScope().sha256()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void peerPendingIdentityOperationBlocksRegistryArtifactUntilPublicationCompletes() {
        var ready = claim(enqueue());
        complete(ready);
        UUID operation = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO academic_identity_operations VALUES (?,'AUTH_COMMITTED')", operation);
        // A different user is in a pending operation; the full registry still includes that scope.
        jdbc.update(
                "INSERT INTO academic_identity_operation_targets VALUES (?,202,TRUE)", operation);
        assertThatThrownBy(() -> jobs.content(jobs.load(ready.id())))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e ->
                                assertThat(e.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        jdbc.update(
                "UPDATE academic_identity_operations SET phase='SUCCEEDED' WHERE operation_id=?",
                operation);
        assertThat(jobs.content(jobs.load(ready.id())))
                .isEqualTo("<html>synthetic</html>".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void availabilityGuardsDenyArtifactReadByExactGroupActorAndWholeRegistryUntilRemoved() {
        var registry = claim(enqueue());
        complete(registry);
        var headman =
                new InternalJwtClaims(
                        101, actor.sessionId(), 1, 1, "HEADMAN", "ACTIVE", 501L, true, false);
        jdbc.update("INSERT INTO groups VALUES (501,TRUE)");
        jdbc.update("INSERT INTO academic_role_eligibility VALUES (101,'HEADMAN','ACTIVE',501)");
        var selector =
                new IssueReportDownloadTicketRequest(
                        ReportDownloadKind.HEADMAN_GROUP_COMPOSITION,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new IssueReportDownloadTicketRequest.HeadmanGroupCompositionParameters(
                                ReportDownloadFormat.HTML));
        var job =
                jobs.enqueue(
                        UUID.randomUUID(),
                        local.plan(headman, selector, Instant.now().plusSeconds(599)),
                        false);
        var scoped = jobs.claim(job, false).orElseThrow();
        byte[] content = "<html>synthetic</html>".getBytes(StandardCharsets.UTF_8);
        jobs.complete(
                scoped.id(), scoped.leaseId(), content, "text/html", 1, false, scoped.identity());
        UUID operation = UUID.randomUUID();
        // No phase lookup may waive a retained guard, even after the operation stopped retrying.
        jdbc.update("INSERT INTO academic_group_availability_guards VALUES (501,?,1)", operation);
        unavailableContent(registry);
        unavailableContent(scoped);
        jdbc.update("DELETE FROM academic_group_availability_guards");
        assertThat(jobs.content(scoped)).isEqualTo(content);
        jdbc.update("INSERT INTO academic_group_availability_guards VALUES (999,?,1)", operation);
        unavailableContent(registry);
        assertThat(jobs.content(scoped)).isEqualTo(content);
        jdbc.update("DELETE FROM academic_group_availability_guards");
        jdbc.update("INSERT INTO academic_group_availability_headmen VALUES (202,?)", operation);
        unavailableContent(registry);
        assertThat(jobs.content(scoped)).isEqualTo(content);
        jdbc.update("DELETE FROM academic_group_availability_headmen");
        jdbc.update("INSERT INTO academic_group_availability_headmen VALUES (101,?)", operation);
        unavailableContent(registry);
        unavailableContent(scoped);
        jdbc.update("DELETE FROM academic_group_availability_headmen");
        assertThat(jobs.content(registry)).isEqualTo(content);
        assertThat(jobs.content(scoped)).isEqualTo(content);
    }

    @Test
    void retainedAvailabilityGuardPreventsReadyPublicationAndSameLeaseCompletesAfterRelease() {
        var preparing = claim(enqueue());
        jdbc.update(
                "INSERT INTO academic_group_availability_guards VALUES (999,?,1)",
                UUID.randomUUID());
        assertThatThrownBy(() -> complete(preparing))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e ->
                                assertThat(e.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(jobs.load(preparing.id()).state()).isEqualTo("preparing");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content IS NULL FROM academic_export_jobs", Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isZero();
        jdbc.update("DELETE FROM academic_group_availability_guards");
        complete(preparing);
        complete(preparing);
        assertThat(jobs.load(preparing.id()).state()).isEqualTo("ready");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class))
                .isEqualTo(1);
    }

    private void unavailableContent(ExportJobRepository.Job job) {
        assertThatThrownBy(() -> jobs.content(job))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e ->
                                assertThat(e.getStatusCode())
                                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void reconnectedServiceLoadsSameArtifactAndCleanupPreservesOtherLiveWork() {
        var completed = claim(enqueue());
        complete(completed);
        var reconnected = new ExportJobRepository(jdbc, mapper, metrics, local);
        assertThat(reconnected.load(completed.id()).id()).isEqualTo(completed.id());
        assertThat(reconnected.content(reconnected.load(completed.id())))
                .isEqualTo("<html>synthetic</html>".getBytes(StandardCharsets.UTF_8));
        var live = enqueue();
        jdbc.update(
                "UPDATE academic_export_jobs SET created_at=NOW()-interval '11"
                        + " minutes',expires_at=NOW()-interval '1 minute' WHERE job_id=?",
                completed.id());
        jobs.cleanExpired();
        assertThat(jobs.load(live.id()).state()).isEqualTo("queued");
        assertThatThrownBy(() -> jobs.load(completed.id()))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shortRetentionCannotCreateInvisibleRetryAndStateRejectsUnknownOrOrdinal() {
        var identity = local.plan(actor, selector("synthetic"), Instant.now().plusSeconds(4));
        var shortJob = claim(jobs.enqueue(UUID.randomUUID(), identity, true));
        jobs.fail(shortJob.id(), shortJob.leaseId(), "GENERATION_FAILED");
        assertThat(jobs.load(shortJob.id()).state()).isEqualTo("error");
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE academic_export_jobs SET state='READY' WHERE"
                                                + " job_id=?",
                                        shortJob.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> mapper.readValue("\"READY\"", ExportJobModels.State.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("2", ExportJobModels.State.class))
                .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
    }

    @Test
    void ownerMetricsSeparateDurableBackoffAndDueWorkWithoutIdentityLabels() {
        var job = claim(enqueue());
        jobs.fail(job.id(), job.leaseId(), "GENERATION_FAILED");
        metrics.refresh();
        assertThat(
                        registry.get("rct.export.jobs.waiting")
                                .tag("kind", "ADMIN_USERS")
                                .gauge()
                                .value())
                .isEqualTo(1);
        assertThat(registry.get("rct.export.jobs.due").tag("kind", "ADMIN_USERS").gauge().value())
                .isZero();
        jdbc.update(
                "UPDATE academic_export_jobs SET lease_until=NOW()-interval '1 second' WHERE"
                        + " job_id=?",
                job.id());
        metrics.refresh();
        assertThat(
                        registry.get("rct.export.jobs.waiting")
                                .tag("kind", "ADMIN_USERS")
                                .gauge()
                                .value())
                .isZero();
        assertThat(registry.get("rct.export.jobs.due").tag("kind", "ADMIN_USERS").gauge().value())
                .isEqualTo(1);
        assertThat(
                        registry.getMeters().stream()
                                .flatMap(m -> m.getId().getTags().stream())
                                .map(t -> t.getKey())
                                .toList())
                .doesNotContain("jobId", "userId", "sessionId", "filename");
    }
}
