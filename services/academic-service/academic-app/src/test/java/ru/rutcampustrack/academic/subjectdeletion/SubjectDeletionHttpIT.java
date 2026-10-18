package ru.rutcampustrack.academic.subjectdeletion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;

import io.grpc.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;
import ru.rutcampustrack.academic.integration.InternalJwtTestConfig;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.authsecurity.grpc.AuthSecurityCapabilityServiceGrpc;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real REST/PG/fences/outbox with explicit Auth and participant transport doubles. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "subject-deletion.retry-delay-ms=3600000",
            "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false",
            "grpc.service-identity.academic-to-auth-token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        })
class SubjectDeletionHttpIT {
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName(
                            "subject_deletion_" + UUID.randomUUID().toString().replace("-", ""))
                    .withUsername("subject_deletion_test")
                    .withPassword("synthetic-local-only")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.flyway.locations", () -> "classpath:db/academic-migration");
        properties.add(
                "spring.autoconfigure.exclude",
                () ->
                        "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                            + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
    }

    @MockitoBean org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;
    @MockitoBean ru.rutcampustrack.shared.outbox.OutboxEventSender outboxEventSender;
    @MockitoBean AcademicLiveSessionClient liveSessions;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired InternalJwtTestFactory tokens;
    @Autowired SubjectDeletionCoordinator coordinator;
    @Autowired SubjectDeletionAcademicData data;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ru.rutcampustrack.academic.semester.SemesterService semesterService;
    @Autowired ru.rutcampustrack.academic.threshold.ThresholdService thresholds;
    @MockitoBean SubjectDeletionParticipants participants;
    @MockitoSpyBean AuthSubjectDeletionClient auth;
    private long subject, foreign;
    private UUID sid;
    private String actor;
    private final java.util.concurrent.ConcurrentMap<
                    UUID, ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt>
            proofs = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean proofAuthority = new AtomicBoolean(true);
    private final AtomicInteger scheduleCommits = new AtomicInteger();
    private final AtomicInteger attendanceCommits = new AtomicInteger();

    private long seedSubject(String name, Long semester) {
        return new TransactionTemplate(transactions)
                .execute(
                        status -> {
                            long id =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects(name,type,group_id,semester_id)"
                                                    + " VALUES(?,'lecture',1,?) RETURNING id",
                                            Long.class,
                                            name,
                                            semester);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    id);
                            return id;
                        });
    }

    @BeforeEach
    void fixture() {
        // This fixture owns a fresh unique ownDB. Historical combined Auth migrations never run.
        try (var connection =
                java.sql.DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(connection.getCatalog()).isEqualTo(POSTGRES.getDatabaseName());
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
            var tables =
                    jdbc.queryForList(
                            "SELECT quote_ident(tablename) FROM pg_tables WHERE"
                                    + " schemaname='public' AND tablename<>'flyway_schema_history'",
                            String.class);
            jdbc.execute("TRUNCATE " + String.join(",", tables) + " RESTART IDENTITY CASCADE");
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException(failure);
        }
        assertThat(
                        jdbc.queryForList(
                                "SELECT tablename FROM pg_tables WHERE schemaname='public' AND"
                                    + " tablename IN"
                                    + " ('users','auth_sessions','user_role_grants','subject_deletion_proofs')",
                                String.class))
                .isEmpty();
        jdbc.update("INSERT INTO groups(id,name) VALUES(1,'Own group'),(2,'Other group')");
        jdbc.update(
                "INSERT INTO"
                    + " semesters(id,name,date_from,date_to,is_active,semester_type,academic_year)"
                    + " VALUES(1,'Own current"
                    + " semester','2026-01-01','2026-06-30',TRUE,'SPRING',2025)");
        jdbc.queryForObject(
                "SELECT setval(pg_get_serial_sequence('semesters','id'),1)", Long.class);
        for (long user : new long[] {2, 3})
            jdbc.update(
                    "INSERT INTO"
                        + " academic_profiles(id,directory_role,directory_status,last_name,first_name,"
                        + "academic_revision,applied_auth_roles_version,created_at,updated_at)"
                        + " VALUES(?,?,'ACTIVE','Synthetic','Own',1,1,NOW(),NOW())",
                    user,
                    user == 2 ? "TEACHER" : "STUDENT");
        jdbc.update(
                "INSERT INTO academic_role_eligibility(user_id,role,status,group_id,updated_at)"
                    + " VALUES(3,'STUDENT','ACTIVE',1,NOW()),(3,'HEADMAN','ACTIVE',1,NOW()),(2,'TEACHER','ACTIVE',NULL,NOW())");
        jdbc.update(
                "INSERT INTO"
                    + " academic_identity_read_model(user_id,catalog_version,login,account_state,roles_version,telegram_binding_version,password_changed,academic_revision)"
                    + " VALUES(2,1,'ownteacher','ACTIVE',1,1,FALSE,1),"
                    + "(3,2,'ownheadman','ACTIVE',1,1,FALSE,1)");
        jdbc.update(
                "INSERT INTO academic_identity_catalog_cursor(singleton,applied_catalog_version)"
                        + " VALUES(TRUE,2)");
        scheduleCommits.set(0);
        attendanceCommits.set(0);
        proofs.clear();
        proofAuthority.set(true);
        sid = UUID.randomUUID();
        actor = tokens.validToken(3, sid, 1, 1, "HEADMAN", "ACTIVE", 1L, true, false);
        when(liveSessions.admit(any()))
                .thenAnswer(
                        call -> {
                            ru.rutcampustrack.shared.security.InternalJwtClaims c =
                                    call.getArgument(0);
                            boolean selected =
                                    ("HEADMAN".equals(c.role())
                                                    && c.isHeadman()
                                                    && (Objects.equals(c.groupId(), 1L)
                                                            || Objects.equals(c.groupId(), 2L)))
                                            || ("STUDENT".equals(c.role())
                                                    && !c.isHeadman()
                                                    && Objects.equals(c.groupId(), 1L));
                            if (c.userId() != 3
                                    || !sid.equals(c.sessionId())
                                    || c.sessionVersion() != 1
                                    || c.rolesVersion() != 1
                                    || !"ACTIVE".equals(c.status())
                                    || c.readOnly()
                                    || !selected) throw Status.UNAUTHENTICATED.asRuntimeException();
                            return new AdminAccessProfile(3, false, "", List.of(), 0);
                        });
        ReflectionTestUtils.setField(
                auth, "stub", AuthSecurityCapabilityServiceGrpc.newBlockingStub(new AuthChannel()));
        subject = seedSubject("Same label", 1L);
        long otherSemester =
                jdbc.queryForObject(
                        "INSERT INTO semesters(name,date_from,date_to,is_active) VALUES('Own old"
                                + " semester','2012-01-01','2012-06-30',FALSE) RETURNING id",
                        Long.class);
        foreign = seedSubject("Same label", otherSemester);
        jdbc.update(
                "INSERT INTO"
                    + " homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,binding_id,actor_id,request_key,payload_hash,publication_state)"
                    + " VALUES(1,?,1,'Own deletion work',3,'2026-02-02',1,?,3,?,?,'ACTIVE')",
                subject,
                1000000 + subject,
                UUID.randomUUID(),
                new byte[32]);
        when(participants.schedule(any())).thenAnswer(call -> receipt(call.getArgument(0), true));
        when(participants.attendance(any()))
                .thenAnswer(call -> receipt(call.getArgument(0), false));
    }

    @Test
    void
            newSubjectIsBoundAndAssignmentRejectsDifferentSemesterWhileLegacyOrdinaryRemainsCompatible()
                    throws Exception {
        var created =
                mvc.perform(
                                signed(post("/academic/subjects"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"New semester-local"
                                                        + " subject\",\"type\":\"LECTURE\"}"))
                        .andReturn()
                        .getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        long id = json.readTree(created.getContentAsString()).path("id").asLong();
        assertThat(id).isPositive();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT semester_id FROM subjects WHERE id=?", Long.class, id))
                .isEqualTo(1);
        long otherSemester =
                jdbc.queryForObject(
                        "SELECT semester_id FROM subjects WHERE id=?", Long.class, foreign);
        var rejected =
                mvc.perform(
                                signed(post("/academic/subjects/" + id + "/teachers/2"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "semesterId",
                                                                otherSemester,
                                                                "lessonType",
                                                                "LECTURE",
                                                                "validFrom",
                                                                "2012-02-01"))))
                        .andReturn()
                        .getResponse();
        assertThat(rejected.getStatus()).isEqualTo(409);
        assertThat(count("SELECT count(*) FROM assignments WHERE subject_id=?", id)).isZero();
        long legacy = seedSubject("Ordinary legacy", null);
        jdbc.update(
                "INSERT INTO"
                    + " assignments(teacher_id,subject_id,group_id,semester_id,lesson_type,valid_from)"
                    + " VALUES(2,?,1,1,'lecture','2026-02-02')",
                legacy);
        jdbc.update(
                "INSERT INTO"
                    + " homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,binding_id,actor_id,request_key,payload_hash,publication_state)"
                    + " VALUES(1,?,1,'Legacy ordinary work',3,'2026-02-02',1,?,3,?,?,'ACTIVE')",
                legacy,
                2000000 + legacy,
                UUID.randomUUID(),
                new byte[32]);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT semester_id IS NULL FROM subjects WHERE id=?",
                                Boolean.class,
                                legacy))
                .isTrue();
        body(signed(get("/academic/subjects/" + legacy + "/deletion-preview")), 409);
    }

    @Test
    void malformedConfirmationIsNoStore400WithoutSecretEchoOrAcceptedOperation() throws Exception {
        String marker = "synthetic-private-proof-" + UUID.randomUUID();
        var response =
                mvc.perform(
                                signed(post(path() + "/deletion"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "operationId",
                                                                UUID.randomUUID(),
                                                                "previewDigest",
                                                                "a".repeat(64),
                                                                "challengeId",
                                                                "S".repeat(32),
                                                                "code",
                                                                marker))))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(!response.getContentAsString().contains(marker)).isTrue();
        assertThat(count("SELECT count(*) FROM subject_deletion_operations")).isZero();
        verifyNoInteractions(auth);
    }

    @Test
    void durableRemoteFailureResumesExactlyOneOwnSubjectAndKeepsOtherSemester() throws Exception {
        var failOnce = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            SubjectDeletionCommand command = call.getArgument(0);
                            if (command.getAction() == SubjectDeletionAction.COMMIT
                                    && failOnce.getAndSet(false))
                                throw new IllegalStateException("Owned participant unavailable");
                            return receipt(command, false);
                        })
                .when(participants)
                .attendance(any());
        JsonNode preview = body(signed(get(path() + "/deletion-preview")), 200);
        UUID operation = UUID.randomUUID(), key = UUID.randomUUID();
        String digest = preview.path("previewDigest").asText();
        body(
                signed(post(path() + "/deletion-challenge"))
                        .content(
                                json.writeValueAsString(
                                        new ChallengeRequest(
                                                operation, digest, "Own-synthetic-password"))),
                200);
        var request = new ConfirmRequest(operation, digest, "S".repeat(32), "123456");
        JsonNode pending = body(confirm(key, request), 202);
        assertThat(pending.path("phase").asText()).isEqualTo("COMMITTING");
        assertThat(pending.path("retryable").asBoolean()).isTrue();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isEqualTo(1);
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isZero();
        JsonNode complete =
                body(signed(get("/academic/subject-deletion-operations/" + operation)), 200);
        assertThat(complete.path("phase").asText()).isEqualTo("COMPLETED");
        assertThat(complete.path("retryable").asBoolean()).isFalse();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isZero();
        assertThat(count("SELECT count(*) FROM homeworks WHERE subject_id=?", subject)).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", foreign)).isEqualTo(1);
        assertThat(body(confirm(key, request), 200).path("phase").asText()).isEqualTo("COMPLETED");
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM academic_outbox WHERE"
                                        + " event_type='subject.deleted' AND"
                                        + " payload->'payload'->>'subject_id'=?",
                                Long.toString(subject)))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_operations WHERE"
                                        + " operation_id=?",
                                operation))
                .isEqualTo(1);
        body(confirm(key, new ConfirmRequest(operation, digest, "T".repeat(32), "123456")), 409);
    }

    @Test
    void committedSubjectIntentRefusesArchiveAndActivationUntilItsReceiptsFinish()
            throws Exception {
        assertThat(
                        mvc.perform(
                                        signed(put("/academic/thresholds/subject"))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .param("subjectId", Long.toString(subject))
                                                .content("{\"minPercentage\":60}"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        String thresholdBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(t)::TEXT FROM attendance_thresholds t WHERE group_id=1 AND"
                                + " subject_id=?",
                        String.class,
                        subject);
        AtomicBoolean checked = new AtomicBoolean();
        doAnswer(
                        call -> {
                            SubjectDeletionCommand c = call.getArgument(0);
                            if (c.getAction() == SubjectDeletionAction.COMMIT) {
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT phase FROM"
                                                            + " subject_deletion_operations WHERE"
                                                            + " operation_id=?",
                                                        String.class,
                                                        UUID.fromString(c.getOperationId())))
                                        .isEqualTo("COMMITTING");
                                assertThatThrownBy(() -> semesterService.beginArchiveTransition(1L))
                                        .isInstanceOf(
                                                ru.rutcampustrack.academic.exception
                                                        .ConflictException.class)
                                        .hasMessageContaining("удаление предмета");
                                long other =
                                        jdbc.queryForObject(
                                                "SELECT semester_id FROM subjects WHERE id=?",
                                                Long.class,
                                                foreign);
                                assertThatThrownBy(() -> semesterService.activateSemester(other))
                                        .isInstanceOf(
                                                ru.rutcampustrack.academic.exception
                                                        .ConflictException.class)
                                        .hasMessageContaining("удаление предмета");
                                assertThatThrownBy(
                                                () ->
                                                        jdbc.update(
                                                                "UPDATE semesters SET"
                                                                        + " is_active=FALSE WHERE"
                                                                        + " id=1"))
                                        .isInstanceOf(
                                                org.springframework.dao.DataAccessException.class);
                                assertThatThrownBy(
                                                () ->
                                                        thresholds.setSubjectThreshold(
                                                                new ru.rutcampustrack.academic
                                                                        .contract.dto.threshold
                                                                        .SetThresholdRequest(75),
                                                                subject))
                                        .isInstanceOf(
                                                org.springframework.dao.DataAccessException.class);
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT to_jsonb(t)::TEXT FROM"
                                                                + " attendance_thresholds t WHERE"
                                                                + " group_id=1 AND subject_id=?",
                                                        String.class,
                                                        subject))
                                        .isEqualTo(thresholdBefore);
                                checked.set(true);
                            }
                            return receipt(c, true);
                        })
                .when(participants)
                .schedule(any());
        JsonNode preview = body(signed(get(path() + "/deletion-preview")), 200);
        UUID operation = UUID.randomUUID();
        JsonNode completed =
                body(
                        confirm(
                                UUID.randomUUID(),
                                new ConfirmRequest(
                                        operation,
                                        preview.path("previewDigest").asText(),
                                        "S".repeat(32),
                                        "123456")),
                        200);
        assertThat(completed.path("phase").asText()).isEqualTo("COMPLETED");
        assertThat(checked.get()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active AND archive_transition='NONE' FROM semesters"
                                        + " WHERE id=1",
                                Boolean.class))
                .isTrue();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isZero();
        assertThat(count("SELECT count(*) FROM attendance_thresholds WHERE subject_id=?", subject))
                .isZero();
    }

    @Test
    void concurrentExactConfirmationProducesOneDurableOperationAndOneCascade() throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        UUID operation = UUID.randomUUID(), key = UUID.randomUUID();
        var request =
                new ConfirmRequest(
                        operation,
                        preview.path("previewDigest").asText(),
                        "S".repeat(32),
                        "123456");
        UUID proof =
                proof(new SubjectDeletionAcademicData.Target(subject, 1L, 1, "", null), request);
        var verifying = new java.util.concurrent.CyclicBarrier(2);
        doAnswer(
                        call -> {
                            verifying.await(5, java.util.concurrent.TimeUnit.SECONDS);
                            return proof;
                        })
                .when(auth)
                .verify(any(), any());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var entered = new java.util.concurrent.CountDownLatch(2);
        var release = new java.util.concurrent.CountDownLatch(1);
        var futures =
                new java.util.ArrayList<
                        java.util.concurrent.Future<
                                org.springframework.mock.web.MockHttpServletResponse>>();
        try {
            for (int i = 0; i < 2; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    entered.countDown();
                                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                                        throw new IllegalStateException(
                                                "Owned subject confirmation release missing");
                                    return mvc.perform(
                                                    confirm(key, request)
                                                            .contentType(
                                                                    MediaType.APPLICATION_JSON))
                                            .andReturn()
                                            .getResponse();
                                }));
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (var future : futures) {
                var response = future.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(response.getStatus() == 200 || response.getStatus() == 202).isTrue();
                assertThat(
                                json.readTree(response.getContentAsString())
                                        .path("operationId")
                                        .asText())
                        .isEqualTo(operation.toString());
            }
        } finally {
            release.countDown();
            for (var future : futures) if (!future.isDone()) future.cancel(true);
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                    .as("owned subject confirmation requests reaped")
                    .isTrue();
        }
        assertThat(
                        body(signed(get("/academic/subject-deletion-operations/" + operation)), 200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_operations WHERE"
                                        + " subject_id=?",
                                subject))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isZero();
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM academic_outbox WHERE"
                                        + " event_type='subject.deleted' AND"
                                        + " payload->'payload'->>'subject_id'=?",
                                Long.toString(subject)))
                .isEqualTo(1);
    }

    @Test
    void stalePreviewUnresolvedLegacyForeignAndNonHeadmanNeverConsumeProofOrPrepare()
            throws Exception {
        JsonNode preview = body(signed(get(path() + "/deletion-preview")), 200);
        jdbc.update("UPDATE homeworks SET title='Changed owned work' WHERE subject_id=?", subject);
        body(
                confirm(
                        UUID.randomUUID(),
                        new ConfirmRequest(
                                UUID.randomUUID(),
                                preview.path("previewDigest").asText(),
                                "S".repeat(32),
                                "123456")),
                409);
        verifyNoInteractions(auth);
        verify(participants, never())
                .schedule(argThat(c -> c.getAction() != SubjectDeletionAction.PREVIEW));
        long legacy = seedSubject("Unresolved legacy", null);
        body(signed(get("/academic/subjects/" + legacy + "/deletion-preview")), 409);
        String student = tokens.validToken(3, sid, 1, 1, "STUDENT", "ACTIVE", 1L, false, false);
        body(get(path() + "/deletion-preview").header("X-Internal-Token", student), 403);
        String outsider = tokens.validToken(3, sid, 1, 1, "HEADMAN", "ACTIVE", 2L, true, false);
        body(get(path() + "/deletion-preview").header("X-Internal-Token", outsider), 403);
        assertThat(count("SELECT count(*) FROM subject_deletion_operations")).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isEqualTo(1);
    }

    @Test
    void revokedAuthorityAfterPrepareReleasesBothBeforeIrreversibleIntent() throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        var revokeOnce = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            SubjectDeletionCommand c = call.getArgument(0);
                            if (c.getAction() == SubjectDeletionAction.SEAL
                                    && revokeOnce.getAndSet(false)) proofAuthority.set(false);
                            return receipt(c, true);
                        })
                .when(participants)
                .schedule(any());
        UUID operation = UUID.randomUUID();
        body(
                confirm(
                        UUID.randomUUID(),
                        new ConfirmRequest(
                                operation,
                                preview.path("previewDigest").asText(),
                                "S".repeat(32),
                                "123456")),
                202);
        coordinator.advance(operation);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM subject_deletion_operations WHERE"
                                        + " operation_id=?",
                                String.class,
                                operation))
                .isEqualTo("CANCELLED");
        assertThat(scheduleCommits.get()).isZero();
        assertThat(attendanceCommits.get()).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isEqualTo(1);
        verify(participants).schedule(argThat(c -> c.getAction() == SubjectDeletionAction.RELEASE));
        verify(participants)
                .attendance(argThat(c -> c.getAction() == SubjectDeletionAction.RELEASE));
    }

    @Test
    void localOutboxRefusalRetainsCommittedRemoteReceiptsAndRollsBackLocalCascade()
            throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        UUID operation = UUID.randomUUID();
        String name = "subject_fact_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute(
                "ALTER TABLE academic_outbox ADD CONSTRAINT "
                        + name
                        + " CHECK(event_type<>'subject.deleted') NOT VALID");
        Throwable primary = null;
        try {
            var pending =
                    body(
                            confirm(
                                    UUID.randomUUID(),
                                    new ConfirmRequest(
                                            operation,
                                            preview.path("previewDigest").asText(),
                                            "S".repeat(32),
                                            "123456")),
                            202);
            assertThat(pending.path("phase").asText()).isEqualTo("COMMITTING");
            assertThat(count("SELECT count(*) FROM homeworks WHERE subject_id=?", subject))
                    .isEqualTo(1);
            assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isEqualTo(1);
        } catch (Exception | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                jdbc.execute("ALTER TABLE academic_outbox DROP CONSTRAINT " + name);
            } catch (RuntimeException failure) {
                var fixed = new IllegalStateException("Owned Academic outbox cleanup failed");
                if (primary != null) primary.addSuppressed(fixed);
                else throw fixed;
            }
        }
        assertThat(
                        body(signed(get("/academic/subject-deletion-operations/" + operation)), 200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isEqualTo(1);
    }

    @Test
    void rejectedProofAndUnadmittedSessionCannotAcceptOrPrepareDeletion() throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        body(
                confirm(
                        UUID.randomUUID(),
                        new ConfirmRequest(
                                UUID.randomUUID(),
                                preview.path("previewDigest").asText(),
                                "S".repeat(32),
                                "999999")),
                403);
        String unadmitted =
                tokens.validToken(
                        99, UUID.randomUUID(), 1, 1, "HEADMAN", "ACTIVE", 1L, true, false);
        var denied =
                mvc.perform(
                                get(path() + "/deletion-preview")
                                        .header("X-Internal-Token", unadmitted))
                        .andReturn()
                        .getResponse();
        assertThat(denied.getStatus()).isEqualTo(401);
        assertThat(count("SELECT count(*) FROM subject_deletion_operations")).isZero();
        assertThat(count("SELECT count(*) FROM subject_deletion_admission_receipts")).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isEqualTo(1);
        verify(participants, never())
                .schedule(argThat(c -> c.getAction() != SubjectDeletionAction.PREVIEW));
    }

    @Test
    void lostAuthVerificationResponseReusesItsExactReceiptOnIdenticalConfirmation()
            throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        var once = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            Object proof = call.callRealMethod();
                            if (once.getAndSet(false))
                                throw AuthSubjectDeletionClient.unavailable();
                            return proof;
                        })
                .when(auth)
                .verify(any(), any());
        UUID operation = UUID.randomUUID(), key = UUID.randomUUID();
        var request =
                new ConfirmRequest(
                        operation,
                        preview.path("previewDigest").asText(),
                        "S".repeat(32),
                        "123456");
        body(confirm(key, request), 503);
        assertThat(count("SELECT count(*) FROM subject_deletion_operations")).isZero();
        UUID consumedReceipt = UUID.fromString(proofs.get(operation).getReceiptId());
        assertThat(body(confirm(key, request), 200).path("phase").asText()).isEqualTo("COMPLETED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT proof_receipt_id FROM subject_deletion_operations WHERE"
                                        + " operation_id=?",
                                UUID.class,
                                operation))
                .isEqualTo(consumedReceipt);
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_admission_receipts WHERE"
                                        + " receipt_id=?",
                                consumedReceipt))
                .isEqualTo(1);
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isEqualTo(1);
    }

    @Test
    void lostParticipantCommitResponseRepeatsOnlyItsIdempotentCommandAndKeepsOneEffect()
            throws Exception {
        var preview = body(signed(get(path() + "/deletion-preview")), 200);
        var committed =
                new java.util.concurrent.ConcurrentHashMap<String, SubjectDeletionReceipt>();
        var once = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            SubjectDeletionCommand command = call.getArgument(0);
                            if (command.getAction() != SubjectDeletionAction.COMMIT)
                                return receipt(command, true);
                            var result =
                                    committed.computeIfAbsent(
                                            command.getOperationId(),
                                            ignored -> receipt(command, true));
                            if (once.getAndSet(false))
                                throw new IllegalStateException(
                                        "Synthetic response lost after participant commit");
                            return result;
                        })
                .when(participants)
                .schedule(any());
        UUID operation = UUID.randomUUID();
        var request =
                new ConfirmRequest(
                        operation,
                        preview.path("previewDigest").asText(),
                        "S".repeat(32),
                        "123456");
        assertThat(body(confirm(UUID.randomUUID(), request), 202).path("phase").asText())
                .isEqualTo("COMMITTING");
        assertThat(scheduleCommits.get()).isEqualTo(1);
        coordinator.resumePending();
        assertThat(
                        body(signed(get("/academic/subject-deletion-operations/" + operation)), 200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(scheduleCommits.get()).isEqualTo(1);
        assertThat(attendanceCommits.get()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", subject)).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE id=?", foreign)).isEqualTo(1);
    }

    @Test
    void localSnapshotRejectsSubjectMovedOutsideItsExactAcceptedTarget() {
        long moving = seedSubject("Moved own subject", 1L);
        var target =
                new SubjectDeletionAcademicData.Target(moving, 1L, 1, "Moved own subject", null);
        jdbc.update("UPDATE subjects SET group_id=2 WHERE id=?", moving);
        assertThatThrownBy(() -> data.snapshot(target))
                .isInstanceOfSatisfying(
                        org.springframework.web.server.ResponseStatusException.class,
                        denied -> assertThat(denied.getStatusCode().value()).isEqualTo(409));
        assertThat(count("SELECT count(*) FROM subject_deletion_operations")).isZero();
        verifyNoInteractions(auth, participants);
    }

    private UUID proof(SubjectDeletionAcademicData.Target target, ConfirmRequest request) {
        return proofs.computeIfAbsent(
                        request.operationId(),
                        ignored ->
                                ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt
                                        .newBuilder()
                                        .setReceiptId(UUID.randomUUID().toString())
                                        .setBinding(
                                                ru.rutcampustrack.authsecurity.grpc
                                                        .SubjectDeletionBinding.newBuilder()
                                                        .setSubjectId(target.subject())
                                                        .setSemesterId(target.semester())
                                                        .setGroupId(target.group())
                                                        .setOperationId(
                                                                request.operationId().toString())
                                                        .setPreviewDigest(request.previewDigest()))
                                        .setActor(
                                                StoredSessionIdentity.newBuilder()
                                                        .setUserId(3)
                                                        .setSessionId(sid.toString())
                                                        .setSessionVersion(1)
                                                        .setRolesVersion(1)
                                                        .setRole("HEADMAN")
                                                        .setStatus("ACTIVE")
                                                        .setGroupId(1)
                                                        .setIsHeadman(true))
                                        .setCredentialsVersion(1)
                                        .setTelegramBindingVersion(1)
                                        .setTelegramId(730000000003L)
                                        .setExpiresAt(
                                                java.time.Instant.now().plusSeconds(120).toString())
                                        .build())
                .getReceiptId()
                .transform(UUID::fromString);
    }

    /**
     * Real generated Auth stub and TLS-scoped CallCredentials, with synthetic service responses.
     */
    private final class AuthChannel extends Channel {
        @Override
        public String authority() {
            return "auth-service";
        }

        @Override
        public <Q, S> ClientCall<Q, S> newCall(MethodDescriptor<Q, S> method, CallOptions options) {
            assertThat(options.getDeadline()).isNotNull();
            assertThat(options.getDeadline().timeRemaining(java.util.concurrent.TimeUnit.SECONDS))
                    .isLessThanOrEqualTo(3);
            return new ClientCall<>() {
                Listener<S> listener;
                Q request;
                Metadata prepared;

                public void start(Listener<S> listener, Metadata headers) {
                    this.listener = listener;
                    prepared = new Metadata();
                    prepared.merge(headers);
                }

                public void request(int amount) {}

                public void cancel(String message, Throwable cause) {}

                public void sendMessage(Q value) {
                    request = value;
                }

                public void halfClose() {
                    assertThat(
                                    org.springframework.transaction.support
                                            .TransactionSynchronizationManager
                                            .isActualTransactionActive())
                            .isFalse();
                    options.getCredentials()
                            .applyRequestMetadata(
                                    new CallCredentials.RequestInfo() {
                                        public MethodDescriptor<?, ?> getMethodDescriptor() {
                                            return method;
                                        }

                                        public SecurityLevel getSecurityLevel() {
                                            return SecurityLevel.PRIVACY_AND_INTEGRITY;
                                        }

                                        public String getAuthority() {
                                            return "auth-service";
                                        }

                                        public Attributes getTransportAttrs() {
                                            return Attributes.EMPTY;
                                        }
                                    },
                                    Runnable::run,
                                    new CallCredentials.MetadataApplier() {
                                        public void fail(Status status) {
                                            listener.onClose(status, new Metadata());
                                        }

                                        @SuppressWarnings("unchecked")
                                        public void apply(Metadata credentialMetadata) {
                                            var metadata = new Metadata();
                                            metadata.merge(prepared);
                                            metadata.merge(credentialMetadata);
                                            assertThat(
                                                            metadata.get(
                                                                    ru.rutcampustrack.shared
                                                                            .security.grpc
                                                                            .DirectedServiceCredential
                                                                            .TOKEN_METADATA_KEY))
                                                    .isNotBlank();
                                            boolean stored =
                                                    method.getBareMethodName()
                                                            .equals(
                                                                    "CheckStoredSubjectDeletionReceipt");
                                            String bearer =
                                                    metadata.get(
                                                            Metadata.Key.of(
                                                                    "x-internal-token",
                                                                    Metadata
                                                                            .ASCII_STRING_MARSHALLER));
                                            if (stored) assertThat(bearer).isNull();
                                            else if (!actor.equals(bearer)) {
                                                fail(Status.UNAUTHENTICATED);
                                                return;
                                            }
                                            Object response;
                                            if (request
                                                    instanceof
                                                    ru.rutcampustrack.authsecurity.grpc
                                                                    .SubjectDeletionChallengeRequest
                                                            c) {
                                                if (!c.getPassword()
                                                        .equals("Own-synthetic-password")) {
                                                    fail(Status.PERMISSION_DENIED);
                                                    return;
                                                }
                                                response =
                                                        ru.rutcampustrack.authsecurity.grpc
                                                                .SubjectDeletionChallenge
                                                                .newBuilder()
                                                                .setChallengeId("S".repeat(32))
                                                                .setAttemptsRemaining(3)
                                                                .setExpiresAt(
                                                                        java.time.Instant.now()
                                                                                .plusSeconds(120)
                                                                                .toString())
                                                                .build();
                                            } else if (request
                                                    instanceof
                                                    ru.rutcampustrack.authsecurity.grpc
                                                                    .SubjectDeletionVerifyRequest
                                                            v) {
                                                if (!v.getCode().equals("123456")
                                                        || !v.getChallengeId()
                                                                .equals("S".repeat(32))) {
                                                    fail(Status.PERMISSION_DENIED);
                                                    return;
                                                }
                                                var binding = v.getBinding();
                                                var receipt =
                                                        proof(
                                                                new SubjectDeletionAcademicData
                                                                        .Target(
                                                                        binding.getSubjectId(),
                                                                        binding.getSemesterId(),
                                                                        binding.getGroupId(),
                                                                        "",
                                                                        null),
                                                                new ConfirmRequest(
                                                                        UUID.fromString(
                                                                                binding
                                                                                        .getOperationId()),
                                                                        binding.getPreviewDigest(),
                                                                        v.getChallengeId(),
                                                                        v.getCode()));
                                                response =
                                                        ru.rutcampustrack.authsecurity.grpc
                                                                .SubjectDeletionVerification
                                                                .newBuilder()
                                                                .setAccepted(true)
                                                                .setReceiptId(receipt.toString())
                                                                .build();
                                            } else if (request
                                                    instanceof
                                                    ru.rutcampustrack.authsecurity.grpc
                                                                    .SubjectDeletionReceiptRequest
                                                            c) {
                                                var receipt =
                                                        proofs.get(
                                                                UUID.fromString(
                                                                        c.getBinding()
                                                                                .getOperationId()));
                                                if (!proofAuthority.get()
                                                        || receipt == null
                                                        || !receipt.getReceiptId()
                                                                .equals(c.getReceiptId())
                                                        || !receipt.getBinding()
                                                                .equals(c.getBinding())) {
                                                    fail(Status.PERMISSION_DENIED);
                                                    return;
                                                }
                                                response = receipt;
                                            } else {
                                                fail(Status.UNIMPLEMENTED);
                                                return;
                                            }
                                            listener.onMessage((S) response);
                                            listener.onClose(Status.OK, new Metadata());
                                        }
                                    });
                }
            };
        }
    }

    private SubjectDeletionReceipt receipt(SubjectDeletionCommand c, boolean schedule) {
        if (c.getAction() == SubjectDeletionAction.COMMIT) {
            if (schedule) scheduleCommits.incrementAndGet();
            else attendanceCommits.incrementAndGet();
        }
        return SubjectDeletionReceipt.newBuilder()
                .setOperationId(c.getOperationId())
                .setSubjectId(c.getSubjectId())
                .setSemesterId(c.getSemesterId())
                .setGroupId(c.getGroupId())
                .setActorId(c.getActorId())
                .setAction(c.getAction())
                .setManifestDigest(c.getManifestDigest())
                .setParticipantDigest(schedule ? "a".repeat(64) : "b".repeat(64))
                .addAllLessons(c.getLessonsList())
                .build();
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder builder) {
        return builder.header("X-Internal-Token", actor);
    }

    private String path() {
        return "/academic/subjects/" + subject;
    }

    private MockHttpServletRequestBuilder confirm(UUID key, ConfirmRequest request)
            throws Exception {
        return signed(post(path() + "/deletion"))
                .header("Idempotency-Key", key)
                .content(json.writeValueAsString(request));
    }

    private JsonNode body(MockHttpServletRequestBuilder request, int status) throws Exception {
        var response =
                mvc.perform(request.contentType(MediaType.APPLICATION_JSON))
                        .andReturn()
                        .getResponse();
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        return json.readTree(response.getContentAsString());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }
}
