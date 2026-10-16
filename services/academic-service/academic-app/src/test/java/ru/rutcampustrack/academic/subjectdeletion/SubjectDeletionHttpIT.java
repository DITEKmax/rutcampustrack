package ru.rutcampustrack.academic.subjectdeletion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Real REST/PG/fences/outbox with explicit Auth and participant transport doubles. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "subject-deletion.retry-delay-ms=3600000")
class SubjectDeletionHttpIT extends AbstractAcademicIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired InternalJwtTestFactory tokens;
    @Autowired SubjectDeletionCoordinator coordinator;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ru.rutcampustrack.academic.semester.SemesterService semesterService;
    @Autowired ru.rutcampustrack.academic.threshold.ThresholdService thresholds;
    @MockitoBean SubjectDeletionParticipants participants;
    @MockitoBean AuthSubjectDeletionClient auth;
    private long subject, foreign;
    private UUID sid;
    private String actor;
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
        scheduleCommits.set(0);
        attendanceCommits.set(0);
        sid = UUID.randomUUID();
        jdbc.update("UPDATE users SET telegram_id=730000000003 WHERE id=3");
        long grant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=3 AND role='headman'",
                        Long.class);
        jdbc.update(
                "INSERT INTO"
                    + " auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)"
                    + " VALUES(?,3,?,1,?,NOW()+INTERVAL '1 hour',NOW(),NOW(),'PASSWORD')",
                sid,
                grant,
                UUID.randomUUID());
        long rv = jdbc.queryForObject("SELECT roles_version FROM users WHERE id=3", Long.class);
        actor = tokens.validToken(3, sid, 1, rv, "HEADMAN", "ACTIVE", 1L, true, false);
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
        when(auth.challenge(any(), any()))
                .thenReturn(
                        new Challenge("S".repeat(32), java.time.Instant.now().plusSeconds(120), 3));
        when(auth.verify(any(), any()))
                .thenAnswer(
                        call -> {
                            SubjectDeletionAcademicData.Target target = call.getArgument(0);
                            ConfirmRequest request = call.getArgument(1);
                            return proof(target, request);
                        });
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
        String student =
                tokens.validToken(
                        3,
                        sid,
                        1,
                        jdbc.queryForObject(
                                "SELECT roles_version FROM users WHERE id=3", Long.class),
                        "STUDENT",
                        "ACTIVE",
                        1L,
                        false,
                        false);
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
                                    && revokeOnce.getAndSet(false))
                                jdbc.update(
                                        "UPDATE user_role_grants SET"
                                            + " status='suspended',updated_at=NOW() WHERE user_id=3"
                                            + " AND role='headman'");
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

    private UUID proof(SubjectDeletionAcademicData.Target target, ConfirmRequest request) {
        UUID receipt = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                    + " subject_deletion_proofs(challenge_hash,operation_id,subject_id,semester_id,group_id,actor_id,session_id,session_version,roles_version,credentials_version,binding_version,telegram_id,preview_digest,code_hash,created_at,expires_at,consumed_at,receipt_id)"
                    + " SELECT"
                    + " ?,?,?,?,1,3,?,1,roles_version,credentials_version,telegram_binding_version,telegram_id,?,?,NOW(),NOW()+INTERVAL"
                    + " '2 minutes',NOW(),? FROM users WHERE id=3",
                "a".repeat(64),
                request.operationId(),
                target.subject(),
                target.semester(),
                sid,
                request.previewDigest(),
                "b".repeat(64),
                receipt);
        return receipt;
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
