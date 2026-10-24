package ru.rutcampustrack.academic.groupdeletion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.groupdeletion.grpc.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actual HTTP, own Academic/Auth PostgreSQL and real directed Auth/password/session admission.
 * Schedule and Attendance transport doubles isolate coordinator unknown-outcome behavior; their
 * real database protocols have separate participant integration tests.
 */
@TestPropertySource(properties = "group-deletion.retry-delay-ms=3600000")
class GroupDeletionHttpIT extends AbstractAcademicIntegrationTest {
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired GroupDeletionCoordinator coordinator;

    @Autowired
    ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations identities;

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoSpyBean GroupDeletionParticipants participants;
    @MockitoSpyBean AuthGroupDeletionClient passwordAuthority;
    private volatile java.util.concurrent.CountDownLatch prepareEntered, prepareReleased;
    private SelectedActor admin, headman, teacher;
    private long group, member, helper, foreign;
    private final AtomicBoolean scheduleUnavailable = new AtomicBoolean();
    private final AtomicBoolean scheduleStale = new AtomicBoolean();
    private final AtomicBoolean prepareUnavailable = new AtomicBoolean();
    private final AtomicBoolean loseAuthCommitReply = new AtomicBoolean();

    @BeforeEach
    void prepare() {
        var fixture = identityFixture.canonical();
        group = fixture.groupId();
        member = fixture.studentId();
        admin = identityFixture.select(fixture.adminId(), AuthRole.ADMIN);
        // A second role on the same account proves that group deletion is not account archival or
        // RV invalidation.
        identityFixture
                .authJdbc()
                .update(
                        "INSERT INTO user_role_grants(user_id,role,status)"
                                + " VALUES(?,'teacher','active')",
                        member);
        jdbc.update(
                "INSERT INTO academic_role_eligibility(user_id,role,status,updated_at)"
                        + " VALUES(?,'TEACHER','ACTIVE',now())",
                member);
        headman = identityFixture.select(member, AuthRole.HEADMAN);
        teacher = identityFixture.select(member, AuthRole.TEACHER);
        helper =
                identityFixture
                        .seedUser(
                                new ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest(
                                        "Помощник",
                                        "Тест",
                                        null,
                                        ru.rutcampustrack.academic.contract.enums.UserRole.STUDENT,
                                        group,
                                        null,
                                        610000000004L),
                                admin)
                        .userId();
        jdbc.update(
                "INSERT INTO headman_assistants(group_id,student_id,permissions,assigned_by)"
                        + " VALUES(?,?,ARRAY['MANAGE_HOMEWORK'],?)",
                group,
                helper,
                member);
        foreign =
                jdbc.queryForObject(
                        "INSERT INTO groups(name) VALUES('Synthetic other preserved') RETURNING id",
                        Long.class);
        new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            long subject =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects(name,type,group_id,semester_id)"
                                                + " VALUES('Own course','lecture',?,?) RETURNING"
                                                + " id",
                                            Long.class,
                                            group,
                                            fixture.semesterId());
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    subject);
                            long foreignSubject =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects(name,type,group_id,semester_id)"
                                                    + " VALUES('Foreign course','lecture',?,?)"
                                                    + " RETURNING id",
                                            Long.class,
                                            foreign,
                                            fixture.semesterId());
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    foreignSubject);
                            long legacy =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects(name,type,group_id)"
                                                    + " VALUES('Legacy group course','lecture',?)"
                                                    + " RETURNING id",
                                            Long.class,
                                            group);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    legacy);
                            long homework =
                                    jdbc.queryForObject(
                                            "INSERT INTO"
                                                + " homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,binding_id,actor_id,request_key,payload_hash,publication_state)"
                                                + " VALUES(?,?,?,'Own deletion"
                                                + " work',?,'2026-02-02',1,1000000,?,?,?,'ACTIVE')"
                                                + " RETURNING id",
                                            Long.class,
                                            group,
                                            subject,
                                            fixture.semesterId(),
                                            member,
                                            member,
                                            UUID.randomUUID(),
                                            new byte[32]);
                            jdbc.update(
                                    "INSERT INTO homework_completions(homework_id,student_id)"
                                            + " VALUES(?,?)",
                                    homework,
                                    member);
                        });
        scheduleUnavailable.set(false);
        scheduleStale.set(false);
        prepareUnavailable.set(false);
        loseAuthCommitReply.set(false);
        prepareEntered = null;
        prepareReleased = null;
        doAnswer(
                        call -> {
                            int index = call.getArgument(0);
                            GroupDeletionCommand command = call.getArgument(1);
                            if (index == 0) {
                                var result = call.callRealMethod();
                                if (command.getAction() == GroupDeletionAction.COMMIT
                                        && loseAuthCommitReply.compareAndSet(true, false))
                                    throw new org.springframework.web.server
                                            .ResponseStatusException(
                                            HttpStatus.SERVICE_UNAVAILABLE,
                                            "owned lost Auth reply after transaction");
                                return result;
                            }
                            if (index == 1
                                    && command.getAction() == GroupDeletionAction.PREPARE
                                    && prepareEntered != null) {
                                var entered = prepareEntered;
                                var released = prepareReleased;
                                prepareEntered = null;
                                entered.countDown();
                                await(released);
                            }
                            if (index == 1
                                    && command.getAction() == GroupDeletionAction.PREPARE
                                    && prepareUnavailable.get())
                                throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "owned delayed prepare");
                            if (index == 1
                                    && command.getAction() == GroupDeletionAction.PREPARE
                                    && scheduleStale.get())
                                throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.CONFLICT, "owned stale participant");
                            if (index == 1
                                    && command.getAction() == GroupDeletionAction.COMMIT
                                    && scheduleUnavailable.get())
                                throw new org.springframework.web.server.ResponseStatusException(
                                        HttpStatus.SERVICE_UNAVAILABLE,
                                        "owned simulated lost transport");
                            String digest = (index == 1 ? "b" : "c").repeat(64);
                            return GroupDeletionReceipt.newBuilder()
                                    .setCommand(command)
                                    .setParticipantDigest(digest)
                                    .build();
                        })
                .when(participants)
                .apply(anyInt(), any());
    }

    @Test
    void passwordPreviewDeletionPreserveAccountOtherRoleAndForeignGroupAndReplay()
            throws Exception {
        long assignedTeacher = identityFixture.canonical().teacherId();
        long legacyTeacher = admin.userId();
        long subject =
                jdbc.queryForObject(
                        "SELECT id FROM subjects WHERE group_id=? AND semester_id IS NOT NULL",
                        Long.class,
                        group);
        long semester = identityFixture.canonical().semesterId();
        jdbc.update(
                "INSERT INTO"
                    + " assignments(teacher_id,subject_id,group_id,semester_id,lesson_type,valid_from)"
                    + " VALUES(?,?,?,?,'lecture','2026-01-01')",
                assignedTeacher,
                subject,
                group,
                semester);
        jdbc.update(
                "INSERT INTO teacher_subject_groups(teacher_id,subject_id,group_id,semester_id)"
                        + " VALUES(?,?,?,?)",
                legacyTeacher,
                subject,
                group,
                semester);
        Set<Long> affected = Set.of(member, helper, assignedTeacher, legacyTeacher);
        String account = authAccountHash(member);
        String localProfile =
                jdbc.queryForObject(
                        "SELECT to_jsonb(p)::text FROM academic_profiles p WHERE id=?",
                        String.class,
                        member);
        String foreignBefore =
                jdbc.queryForObject(
                        "SELECT to_jsonb(g)::text FROM groups g WHERE id=?", String.class, foreign);
        JsonNode preview =
                body(
                        call(
                                admin,
                                HttpMethod.GET,
                                "/academic/groups/" + group + "/deletion-preview",
                                null,
                                null),
                        200);
        assertThat(preview.path("counts").path("memberships").asLong()).isEqualTo(2);
        assertThat(preview.path("counts").path("headmanAssignments").asLong()).isEqualTo(1);
        assertThat(preview.path("counts").path("assistantAssignments").asLong()).isEqualTo(1);
        UUID wrongKey = UUID.randomUUID();
        String digest = preview.path("previewDigest").asText();
        body(
                call(
                        admin,
                        HttpMethod.POST,
                        "/academic/groups/" + group + "/deletions",
                        request("wrong synthetic password", digest),
                        wrongKey),
                403);
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isZero();
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        var cancelled =
                body(call(admin, HttpMethod.POST, resolvePath(group, wrongKey), null, null), 200);
        assertThat(cancelled.path("phase").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.path("failure").asText()).isEqualTo("NOT_ACCEPTED");
        assertThat(cancelled.path("counts").path("academicRows").asLong()).isZero();
        body(
                call(
                        admin,
                        HttpMethod.POST,
                        "/academic/groups/" + group + "/deletions",
                        request(
                                AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                                digest),
                        wrongKey),
                409);
        UUID key = UUID.randomUUID();
        body(
                call(
                        teacher,
                        HttpMethod.POST,
                        "/academic/groups/" + group + "/deletions",
                        request("any", digest),
                        UUID.randomUUID()),
                403);
        var completed =
                body(
                        call(
                                admin,
                                HttpMethod.POST,
                                "/academic/groups/" + group + "/deletions",
                                request(
                                        AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                                        digest),
                                key),
                        200);
        assertThat(completed.path("phase").asText()).isEqualTo("COMPLETED");
        String committedHint = groupHint();
        assertThat(json.readTree(committedHint).path("payload").path("affected_user_ids"))
                .satisfies(
                        ids -> {
                            Set<Long> actual = new HashSet<>();
                            ids.forEach(value -> actual.add(value.asLong()));
                            assertThat(actual).containsExactlyInAnyOrderElementsOf(affected);
                            assertThat(ids.size()).isEqualTo(affected.size());
                        });
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE group_id=" + group)).isZero();
        assertThat(count("SELECT count(*) FROM homeworks WHERE group_id=" + group)).isZero();
        assertThat(count("SELECT count(*) FROM homework_completions")).isZero();
        assertThat(count("SELECT count(*) FROM subjects WHERE group_id=" + foreign)).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM academic_role_eligibility WHERE user_id="
                                        + member
                                        + " AND role='TEACHER'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM headman_assistants WHERE group_id=" + group))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(p)::text FROM academic_profiles p WHERE id=?",
                                String.class,
                                member))
                .isEqualTo(localProfile);
        assertThat(authAccountHash(member)).isEqualTo(account);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(g)::text FROM groups g WHERE id=?",
                                String.class,
                                foreign))
                .isEqualTo(foreignBefore);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=? AND"
                                                + " role='teacher' AND status='active'",
                                        Long.class,
                                        member))
                .isEqualTo(1L);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT account_state='ACTIVE' AND password_hash IS NOT"
                                                + " NULL FROM auth_identities WHERE id=?",
                                        Boolean.class,
                                        helper))
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        helper))
                .isZero();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                        Long.class,
                                        group))
                .isZero();
        assertThat(
                        call(teacher, HttpMethod.GET, "/academic/users/me", null, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        assertThat(
                        call(headman, HttpMethod.GET, "/academic/users/me", null, null)
                                .getStatusCode()
                                .value())
                .isIn(401, 403);
        body(call(admin, HttpMethod.GET, "/academic/group-deletions/" + key, null, null), 200);
        // A known completed operation does not ask Auth to verify or mutate again.
        assertThat(
                        body(
                                call(
                                        admin,
                                        HttpMethod.POST,
                                        "/academic/groups/" + group + "/deletions",
                                        request("unused after completion", digest),
                                        key),
                                200))
                .isEqualTo(completed);
        body(
                call(
                        admin,
                        HttpMethod.POST,
                        "/academic/groups/" + foreign + "/deletions",
                        request("unused", digest),
                        key),
                409);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE group_deletion_operations SET actor_id=99 WHERE"
                                                + " operation_id=?",
                                        key))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(groupHint()).isEqualTo(committedHint);
        // The preserved, now grantless helper remains usable by the ordinary identity CAS flow.
        try (var ignored = identityFixture.request(admin, UUID.randomUUID())) {
            assertThat(
                            identities
                                    .role(
                                            helper,
                                            "TEACHER",
                                            new ru.rutcampustrack.academic.contract.dto.user
                                                    .RoleGrantUpdateRequest(
                                                    ru.rutcampustrack.academic.contract.enums
                                                            .RoleGrantStatus.ACTIVE,
                                                    null,
                                                    "T00002",
                                                    null))
                                    .phase())
                    .isEqualTo(
                            ru.rutcampustrack.academic.identityoperation.AcademicIdentityCoordinator
                                    .Phase.SUCCEEDED);
        }
    }

    @Test
    void stalePreviewAndDurablePartialUnknownResumeOnlyOriginalOperation() throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID staleKey = UUID.randomUUID();
        jdbc.update("UPDATE groups SET name=name||' changed' WHERE id=?", group);
        body(
                call(
                        admin,
                        HttpMethod.POST,
                        "/academic/groups/" + group + "/deletions",
                        request(
                                AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                                digest),
                        staleKey),
                409);
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isZero();
        body(call(admin, HttpMethod.GET, "/academic/group-deletions/" + staleKey, null, null), 404);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.POST,
                                                resolvePath(group, staleKey),
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("CANCELLED");
        digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        scheduleUnavailable.set(true);
        loseAuthCommitReply.set(true);
        UUID key = UUID.randomUUID();
        JsonNode pending =
                body(
                        call(
                                admin,
                                HttpMethod.POST,
                                "/academic/groups/" + group + "/deletions",
                                request(
                                        AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                                        digest),
                                key),
                        202);
        assertThat(pending.path("phase").asText()).isEqualTo("COMMITTING");
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                        Long.class,
                                        group))
                .isZero();
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "UPDATE groups SET name='forbidden during partial outcome'"
                                                + " WHERE id=?",
                                        group))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(
                        call(teacher, HttpMethod.GET, "/academic/users/me", null, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        identityFixture.revokeCurrent(admin);
        admin = identityFixture.select(admin.userId(), AuthRole.ADMIN);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.GET,
                                                "/academic/group-deletions/" + key,
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMMITTING");
        scheduleUnavailable.set(false);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.GET,
                                                "/academic/group-deletions/" + key,
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isZero();
    }

    @Test
    void staleParticipantReleasesPreparedAuthWithoutLosingMembershipOrAuthority() throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        scheduleStale.set(true);
        UUID key = UUID.randomUUID();
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.POST,
                                                "/academic/groups/" + group + "/deletions",
                                                request(
                                                        AcademicIdentityIntegrationFixture
                                                                .FIRST_FULL_TEST_PASSWORD,
                                                        digest),
                                                key),
                                        202)
                                .path("phase")
                                .asText())
                .isEqualTo("RELEASING");
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.GET,
                                                "/academic/group-deletions/" + key,
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("CANCELLED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                        Long.class,
                                        group))
                .isEqualTo(3L);
        assertThat(count("SELECT count(*) FROM academic_role_eligibility WHERE group_id=" + group))
                .isEqualTo(3);
        assertThat(
                        call(headman, HttpMethod.GET, "/academic/users/me", null, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        jdbc.update("UPDATE groups SET name=name||' remains writable' WHERE id=?", group);
    }

    @Test
    void delayedPrepareCannotCommitAfterOriginalSessionLogout() throws Exception {
        delayedPrepareCannotCommitAfterWithdrawal("logout");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"permission", "credentials", "expiry"})
    void delayedPrepareCannotCommitAfterOriginalAuthorityOrProofIsWithdrawn(String change)
            throws Exception {
        delayedPrepareCannotCommitAfterWithdrawal(change);
    }

    private void delayedPrepareCannotCommitAfterWithdrawal(String change) throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID key = UUID.randomUUID();
        prepareUnavailable.set(true);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.POST,
                                                "/academic/groups/" + group + "/deletions",
                                                request(
                                                        AcademicIdentityIntegrationFixture
                                                                .FIRST_FULL_TEST_PASSWORD,
                                                        digest),
                                                key),
                                        202)
                                .path("phase")
                                .asText())
                .isEqualTo("PREPARING");
        var auth = identityFixture.authJdbc();
        switch (change) {
            case "logout" -> identityFixture.revokeCurrent(admin);
            case "permission" ->
                    auth.update(
                            "UPDATE admin_access_profiles SET"
                                + " mode='LIMITED',permissions=ARRAY['GROUPS_MANAGE'],version=version+1"
                                + " WHERE user_id=?",
                            admin.userId());
            case "credentials" ->
                    identityFixture.changePassword(
                            admin,
                            AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                            "Changed-Group-Only-Password-77!");
            case "expiry" -> {
                // Own fixture clock boundary, no wait or production proof mutation.
                var owner = identityFixture.fixtureOwnerAuthJdbc();
                owner.execute(
                        "ALTER TABLE auth_password_step_up_receipts DISABLE TRIGGER"
                                + " auth_password_step_up_receipt_immutable");
                try {
                    owner.update(
                            "UPDATE auth_password_step_up_receipts SET"
                                    + " created_at=created_at-interval '120"
                                    + " seconds',expires_at=expires_at-interval '120 seconds' WHERE"
                                    + " operation_id=?",
                            key);
                } finally {
                    owner.execute(
                            "ALTER TABLE auth_password_step_up_receipts ENABLE TRIGGER"
                                    + " auth_password_step_up_receipt_immutable");
                }
            }
            default -> throw new IllegalArgumentException(change);
        }
        prepareUnavailable.set(false);
        coordinator.advance(key);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM group_deletion_operations WHERE operation_id=?",
                                String.class,
                                key))
                .isEqualTo("RELEASING");
        assertThat(
                        auth.queryForObject(
                                "SELECT state FROM auth_group_deletion_participants WHERE"
                                        + " operation_id=?",
                                String.class,
                                key))
                .isEqualTo("RELEASED");
        coordinator.advance(key);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM group_deletion_operations WHERE operation_id=?",
                                String.class,
                                key))
                .isEqualTo("CANCELLED");
        assertThat(
                        auth.queryForObject(
                                "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                Long.class,
                                group))
                .isEqualTo(3L);
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subjects WHERE group_id=" + group)).isEqualTo(2);
        assertThat(
                        call(headman, HttpMethod.GET, "/academic/users/me", null, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        jdbc.update("UPDATE groups SET name=name||' still writable' WHERE id=?", group);
    }

    @Test
    void lostNegativeAuthCommitReplyKeepsIntentUntilOriginalReceiptProvesZeroEffects()
            throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID key = UUID.randomUUID();
        prepareUnavailable.set(true);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.POST,
                                                "/academic/groups/" + group + "/deletions",
                                                request(
                                                        AcademicIdentityIntegrationFixture
                                                                .FIRST_FULL_TEST_PASSWORD,
                                                        digest),
                                                key),
                                        202)
                                .path("phase")
                                .asText())
                .isEqualTo("PREPARING");
        identityFixture.revokeCurrent(admin);
        prepareUnavailable.set(false);
        loseAuthCommitReply.set(true);
        coordinator.advance(key);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM group_deletion_operations WHERE operation_id=?",
                                String.class,
                                key))
                .isEqualTo("COMMITTING");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT state FROM auth_group_deletion_participants WHERE"
                                                + " operation_id=?",
                                        String.class,
                                        key))
                .isEqualTo("RELEASED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                        Long.class,
                                        group))
                .isEqualTo(3);
        coordinator.advance(key);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM group_deletion_operations WHERE operation_id=?",
                                String.class,
                                key))
                .isEqualTo("RELEASING");
        coordinator.advance(key);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM group_deletion_operations WHERE operation_id=?",
                                String.class,
                                key))
                .isEqualTo("CANCELLED");
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM subjects WHERE group_id=" + group)).isEqualTo(2);
    }

    @Test
    void crossGroupThresholdIsDeniedAndHistoricalForeignReferenceBlocksPreviewAndCascade()
            throws Exception {
        long ownSubject =
                jdbc.queryForObject(
                        "SELECT id FROM subjects WHERE group_id=? AND semester_id IS NOT NULL",
                        Long.class,
                        group);
        long foreignSubject =
                jdbc.queryForObject(
                        "SELECT id FROM subjects WHERE group_id=?", Long.class, foreign);
        // The actual headman endpoint cannot adopt another group's subject.
        assertThat(
                        call(
                                        headman,
                                        HttpMethod.PUT,
                                        "/academic/thresholds/subject?subjectId=" + foreignSubject,
                                        Map.of("minPercentage", 75),
                                        null)
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO"
                                            + " attendance_thresholds(group_id,subject_id,threshold_pct,set_by)"
                                            + " VALUES(?,?,75,?)",
                                        foreign,
                                        ownSubject,
                                        member))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // Historical inconsistency is reachable in the old schema. Construct ONLY in this owned DB.
        var owner = identityFixture.fixtureOwnerAcademicJdbc();
        owner.execute("ALTER TABLE attendance_thresholds DISABLE TRIGGER threshold_group_scope");
        try {
            owner.update(
                    "INSERT INTO attendance_thresholds(group_id,subject_id,threshold_pct,set_by)"
                            + " VALUES(?,?,75,?)",
                    foreign,
                    ownSubject,
                    member);
        } finally {
            owner.execute("ALTER TABLE attendance_thresholds ENABLE TRIGGER threshold_group_scope");
        }
        String before =
                jdbc.queryForObject(
                        "SELECT to_jsonb(t)::text FROM attendance_thresholds t WHERE group_id=?",
                        String.class,
                        foreign);
        body(
                call(
                        admin,
                        HttpMethod.GET,
                        "/academic/groups/" + group + "/deletion-preview",
                        null,
                        null),
                409);
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isZero();
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(t)::text FROM attendance_thresholds t WHERE"
                                        + " group_id=?",
                                String.class,
                                foreign))
                .isEqualTo(before);
    }

    @Test
    void localOutboxFailureRollsBackErasureAndOriginalCommittedIntentResumes() throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID key = UUID.randomUUID();
        String constraint = "group_delete_outbox_" + UUID.randomUUID().toString().replace("-", "");
        var owner = identityFixture.fixtureOwnerAcademicJdbc();
        owner.execute(
                "ALTER TABLE academic_outbox ADD CONSTRAINT "
                        + constraint
                        + " CHECK(event_type<>'group.updated') NOT VALID");
        try {
            assertThat(
                            body(
                                            call(
                                                    admin,
                                                    HttpMethod.POST,
                                                    "/academic/groups/" + group + "/deletions",
                                                    request(
                                                            AcademicIdentityIntegrationFixture
                                                                    .FIRST_FULL_TEST_PASSWORD,
                                                            digest),
                                                    key),
                                            202)
                                    .path("phase")
                                    .asText())
                    .isEqualTo("COMMITTING");
            assertThat(
                            count(
                                    "SELECT count(*) FROM academic_outbox WHERE"
                                        + " event_type='group.updated' AND payload #>"
                                        + " '{payload,affected_user_ids}' IS NOT NULL AND payload"
                                        + " #>> '{payload,group_id}'='"
                                            + group
                                            + "'"))
                    .isZero();
            assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM subjects WHERE group_id=" + group)).isEqualTo(2);
            assertThat(
                            count(
                                    "SELECT count(*) FROM academic_role_eligibility WHERE group_id="
                                            + group))
                    .isEqualTo(3);
            assertThat(
                            identityFixture
                                    .authJdbc()
                                    .queryForObject(
                                            "SELECT count(*) FROM user_role_grants WHERE"
                                                    + " group_id=?",
                                            Long.class,
                                            group))
                    .isZero();
        } finally {
            owner.execute("ALTER TABLE academic_outbox DROP CONSTRAINT " + constraint);
        }
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.GET,
                                                "/academic/group-deletions/" + key,
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isZero();
        JsonNode recipients = json.readTree(groupHint()).path("payload").path("affected_user_ids");
        Set<Long> actual = new HashSet<>();
        recipients.forEach(value -> actual.add(value.asLong()));
        assertThat(actual).containsExactlyInAnyOrder(member, helper);
    }

    private String groupHint() {
        var hints =
                jdbc.queryForList(
                        "SELECT payload::text FROM academic_outbox WHERE event_type='group.updated'"
                            + " AND payload #> '{payload,affected_user_ids}' IS NOT NULL AND"
                            + " payload #>> '{payload,group_id}'=?",
                        String.class,
                        Long.toString(group));
        assertThat(hints).hasSize(1);
        return hints.getFirst();
    }

    @Test
    void resolveWinsAgainstInFlightPasswordProofAndPermanentTombstoneBlocksLateAcceptance()
            throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID key = UUID.randomUUID();
        var proofReady = new java.util.concurrent.CountDownLatch(1);
        var proofReleased = new java.util.concurrent.CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            var proof = invocation.callRealMethod();
                            proofReady.countDown();
                            await(proofReleased);
                            return proof;
                        })
                .when(passwordAuthority)
                .confirm(eq(group), eq(key), eq(digest), anyString());
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var confirming =
                    workers.submit(
                            () ->
                                    call(
                                            admin,
                                            HttpMethod.POST,
                                            "/academic/groups/" + group + "/deletions",
                                            request(
                                                    AcademicIdentityIntegrationFixture
                                                            .FIRST_FULL_TEST_PASSWORD,
                                                    digest),
                                            key));
            try {
                await(proofReady);
                var resolved =
                        body(
                                call(admin, HttpMethod.POST, resolvePath(group, key), null, null),
                                200);
                assertThat(resolved.path("phase").asText()).isEqualTo("CANCELLED");
                assertThat(resolved.path("failure").asText()).isEqualTo("NOT_ACCEPTED");
                assertThat(resolved.path("retryable").asBoolean()).isFalse();
                resolved.path("counts").forEach(value -> assertThat(value.asLong()).isZero());
            } finally {
                proofReleased.countDown();
            }
            body(confirming.get(15, java.util.concurrent.TimeUnit.SECONDS), 409);
        }
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE group_id=?",
                                        Long.class,
                                        group))
                .isEqualTo(3);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM auth_group_deletion_participants"
                                                + " WHERE operation_id=?",
                                        Long.class,
                                        key))
                .isZero();
        var newSession = identityFixture.select(admin.userId(), AuthRole.ADMIN);
        assertThat(
                        body(
                                        call(
                                                newSession,
                                                HttpMethod.POST,
                                                resolvePath(group, key),
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("CANCELLED");
        body(
                call(
                        admin,
                        HttpMethod.POST,
                        "/academic/groups/" + group + "/deletions",
                        request(
                                AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD,
                                digest),
                        key),
                409);
    }

    @Test
    void acceptanceWinsResolveRaceAndResolverCannotCancelAcceptedIntent() throws Exception {
        String digest =
                body(
                                call(
                                        admin,
                                        HttpMethod.GET,
                                        "/academic/groups/" + group + "/deletion-preview",
                                        null,
                                        null),
                                200)
                        .path("previewDigest")
                        .asText();
        UUID key = UUID.randomUUID();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var released = new java.util.concurrent.CountDownLatch(1);
        prepareEntered = entered;
        prepareReleased = released;
        prepareUnavailable.set(true);
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var confirming =
                    workers.submit(
                            () ->
                                    call(
                                            admin,
                                            HttpMethod.POST,
                                            "/academic/groups/" + group + "/deletions",
                                            request(
                                                    AcademicIdentityIntegrationFixture
                                                            .FIRST_FULL_TEST_PASSWORD,
                                                    digest),
                                            key));
            try {
                await(entered); // acceptance is durable, confirm HTTP request is still in flight.
                var resolving =
                        workers.submit(
                                () ->
                                        call(
                                                admin,
                                                HttpMethod.POST,
                                                resolvePath(group, key),
                                                null,
                                                null));
                released.countDown();
                assertThat(
                                body(confirming.get(15, java.util.concurrent.TimeUnit.SECONDS), 202)
                                        .path("phase")
                                        .asText())
                        .isEqualTo("PREPARING");
                var resolved = body(resolving.get(15, java.util.concurrent.TimeUnit.SECONDS), 200);
                assertThat(resolved.path("phase").asText()).isEqualTo("PREPARING");
                assertThat(resolved.path("counts").path("memberships").asLong()).isEqualTo(2);
            } finally {
                released.countDown();
            }
        }
        prepareUnavailable.set(false);
        assertThat(
                        body(
                                        call(
                                                admin,
                                                HttpMethod.GET,
                                                "/academic/group-deletions/" + key,
                                                null,
                                                null),
                                        200)
                                .path("phase")
                                .asText())
                .isEqualTo("COMPLETED");
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isZero();
    }

    @Test
    void resolverRequiresCurrentAdminAndCannotReadOrCancelAnotherActorOrGroup() throws Exception {
        UUID key = UUID.randomUUID();
        body(call(headman, HttpMethod.POST, resolvePath(group, key), null, null), 403);
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isZero();
        body(call(admin, HttpMethod.POST, resolvePath(group, key), null, null), 200);
        // Add an independent administrator only to this owned fixture; target group roles remain.
        identityFixture
                .authJdbc()
                .update(
                        "INSERT INTO user_role_grants(user_id,role,status)"
                                + " VALUES(?,'admin','active')",
                        helper);
        identityFixture
                .authJdbc()
                .update(
                        "INSERT INTO"
                            + " admin_access_profiles(user_id,assigned,mode,permissions,version)"
                            + " VALUES(?,true,'FULL','{}',1) ON CONFLICT(user_id) DO UPDATE SET"
                            + " assigned=true,mode='FULL',permissions='{}',version=admin_access_profiles.version+1",
                        helper);
        var otherAdmin = identityFixture.select(helper, AuthRole.ADMIN);
        body(call(otherAdmin, HttpMethod.POST, resolvePath(group, key), null, null), 404);
        body(call(admin, HttpMethod.POST, resolvePath(foreign, key), null, null), 404);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor_id FROM group_deletion_operations WHERE"
                                        + " operation_id=?",
                                Long.class,
                                key))
                .isEqualTo(admin.userId());
        assertThat(count("SELECT count(*) FROM group_deletion_operations")).isEqualTo(1);
        long absentGroup = 9007199254740993L;
        var absent =
                body(
                        call(
                                admin,
                                HttpMethod.POST,
                                resolvePath(absentGroup, UUID.randomUUID()),
                                null,
                                null),
                        200);
        assertThat(absent.path("phase").asText()).isEqualTo("CANCELLED");
        assertThat(absent.path("groupId").asText()).isEqualTo(Long.toString(absentGroup));
        assertThat(count("SELECT count(*) FROM groups WHERE id=" + group)).isEqualTo(1);
    }

    private String resolvePath(long target, UUID key) {
        return "/academic/groups/" + target + "/deletions/" + key + "/resolve";
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            assertThat(latch.await(15, java.util.concurrent.TimeUnit.SECONDS))
                    .as("owned race barrier")
                    .isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private Map<String, String> request(String password, String digest) {
        return Map.of("password", password, "previewDigest", digest);
    }

    private ResponseEntity<String> call(
            SelectedActor actor, HttpMethod method, String path, Object body, UUID key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Token", actor.internalToken());
        if (key != null) headers.set("Idempotency-Key", key.toString());
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode body(ResponseEntity<String> response, int expected) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
        String cacheControl = response.getHeaders().getCacheControl();
        assertThat(cacheControl).isNotBlank();
        // Repeated identical directives from filter/controller retain the same privacy guarantee.
        assertThat(Arrays.stream(cacheControl.split(",")).map(String::trim).toList())
                .containsOnly("no-store");
        return json.readTree(response.getBody());
    }

    private String authAccountHash(long user) {
        String row =
                identityFixture
                        .authJdbc()
                        .queryForObject(
                                "SELECT (to_jsonb(i)-'catalog_version')::text FROM auth_identities"
                                        + " i WHERE id=?",
                                String.class,
                                user);
        return ru.rutcampustrack.academic.subjectdeletion.SubjectDeletionAcademicData.hash(row);
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
