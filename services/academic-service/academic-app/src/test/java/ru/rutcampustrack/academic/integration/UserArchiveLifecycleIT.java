package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.grpc.Status;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.assistant.AssistantService;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.user.*;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class UserArchiveLifecycleIT extends AbstractAcademicIntegrationTest {
    private static final String OWNED_SEMESTER_NAME = "user-archive-" + UUID.randomUUID();
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired UserService users;
    @Autowired GroupService groups;
    @MockitoSpyBean UserArchiveRepository repository;
    @Autowired UserRoleGrantReader grants;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean GroupRepository groupRepository;
    @Autowired HeadmanAssistantRepository assistantRepository;
    @Autowired AssistantService assistant;
    @Autowired UserArchiveService service;
    @Autowired ru.rutcampustrack.academic.security.AcademicLiveSessionClient liveSessions;
    @MockitoSpyBean AuthUserArchiveClient auth;
    @MockitoBean AttendanceUserImpactGrpcClient attendance;
    private long owner, target, semester, group;
    private UUID session;
    private SelectedActor adminActor, studentActor, headmanActor;
    private LocalDate today;

    @BeforeEach
    void seedOwnedLifecycle() {
        today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        jdbc.update("UPDATE semesters SET is_active=FALSE WHERE is_active");
        var ownedSemesters =
                jdbc.queryForList(
                        "SELECT id FROM semesters WHERE name=?", Long.class, OWNED_SEMESTER_NAME);
        if (ownedSemesters.isEmpty()) {
            semester =
                    jdbc.queryForObject(
                            "INSERT INTO semesters(name,date_from,date_to,is_active,created_at)"
                                    + " VALUES(?,?,?,TRUE,NOW()) RETURNING id",
                            Long.class,
                            OWNED_SEMESTER_NAME,
                            today.minusDays(5),
                            today.plusDays(5));
        } else {
            semester = ownedSemesters.getFirst();
            jdbc.update("UPDATE semesters SET is_active=TRUE WHERE id=?", semester);
        }
        group =
                groups.createGroup(
                                new CreateGroupRequest(
                                        "УАР-11" + Math.floorMod(System.nanoTime(), 10)))
                        .getId();
        owner = identityFixture.canonical().adminId();
        adminActor = identityFixture.select(owner, AuthRole.ADMIN);
        target =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Archive",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        610000008001L),
                                adminActor)
                        .userId();
        try (var scope = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    target,
                    "TEACHER",
                    new RoleGrantUpdateRequest(
                            RoleGrantStatus.ACTIVE, null, "archive-employee-" + target, null));
        }
        try (var scope = identityFixture.request(adminActor)) {
            users.patchUser(
                    target, new PatchUserRequest(null, null, null, true, null, null, null, null));
        }
        studentActor = identityFixture.select(target, AuthRole.STUDENT);
        headmanActor = identityFixture.select(target, AuthRole.HEADMAN);
        session = studentActor.sessionId();
        when(attendance.preview(target))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(target)
                                .setAttendanceMarksCount(7)
                                .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                .setSnapshotDigest("b".repeat(64))
                                .build());
    }

    private Preview preview(long user) {
        try (var scope = identityFixture.request(adminActor)) {
            return service.preview(user);
        }
    }

    private void archive(long user, ArchiveRequest request) {
        try (var scope = identityFixture.request(adminActor, request.operationId())) {
            service.archive(user, request);
        }
    }

    private void restore(long user, RestoreRequest request) {
        try (var scope = identityFixture.request(adminActor, request.operationId())) {
            service.restore(user, request);
        }
    }

    private void requireOldSessionDenied() throws Exception {
        assertThatThrownBy(() -> liveSessions.admit(studentActor.claims()))
                .satisfies(
                        failure ->
                                assertThat(Status.fromThrowable(failure).getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
        mockMvc.perform(get("/academic/users/me").with(identityFixture.signed(studentActor)))
                .andExpect(status().isUnauthorized());
    }

    // The owned reset precedes each test. Retained profiles/history are not
    // deleted through application tables as teardown (V51 uses RESTRICT).

    @Test
    void protectedArchiveReceiptRollsBackAndRestoreRequiresFreshRoleAssignment() throws Exception {
        // Reuse HomeworkStudentCompletionConcurrencyIT's persisted fixture shape.
        // This is retained personal history, not a new homework-creation flow.
        long subject =
                jdbc.queryForObject(
                        """
                        WITH subject AS (
                            INSERT INTO subjects(name,type,group_id)
                            VALUES (?, 'lecture', ?) RETURNING id,type
                        ), lesson_type AS (
                            INSERT INTO subject_lesson_types(subject_id,lesson_type)
                            SELECT id,type FROM subject RETURNING subject_id
                        ) SELECT subject_id FROM lesson_type
                        """,
                        Long.class,
                        "Archive retained homework " + UUID.randomUUID(),
                        group);
        OffsetDateTime completedAt =
                today.minusDays(1).atTime(19, 23, 41).atOffset(ZoneOffset.ofHours(3));
        long homework =
                jdbc.queryForObject(
                        """
INSERT INTO homeworks(group_id,subject_id,semester_id,lesson_date,lesson_number,
    title,description,link,published_by,binding_id,actor_id,request_key,payload_hash,
    publication_state,created_at,updated_at)
VALUES(?,?,?,?,1,?,?,?, ?,?,?,?,decode(repeat('11',32),'hex'),'ACTIVE',?,?)
RETURNING id
""",
                        Long.class,
                        group,
                        subject,
                        semester,
                        today.minusDays(1),
                        "Сохранённое задание",
                        "Личная история до архивации аккаунта",
                        "https://example.invalid/retained-homework",
                        target,
                        subject,
                        target,
                        UUID.randomUUID(),
                        completedAt.minusHours(4),
                        completedAt.minusHours(4));
        long completion =
                jdbc.queryForObject(
                        "INSERT INTO homework_completions(homework_id,student_id,completed_at)"
                                + " VALUES(?,?,?) RETURNING id",
                        Long.class,
                        homework,
                        target,
                        completedAt);
        String retainedHomeworkSql =
                """
                SELECT to_jsonb(h)::text AS homework, to_jsonb(c)::text AS completion
                FROM homeworks h JOIN homework_completions c ON c.homework_id=h.id
                WHERE h.id=? AND c.id=? AND c.student_id=?
                """;
        var retainedHomework = jdbc.queryForList(retainedHomeworkSql, homework, completion, target);
        assertThat(retainedHomework).hasSize(1);
        var before =
                identityFixture
                        .authJdbc()
                        .queryForMap(
                                "SELECT login,password_hash,initial_password,telegram_id FROM"
                                        + " auth_identities WHERE id=?",
                                target);
        var grantIds =
                identityFixture
                        .authJdbc()
                        .queryForList(
                                "SELECT id FROM user_role_grants WHERE user_id=? ORDER BY id",
                                Long.class,
                                target);
        Preview preview = preview(target);
        assertThat(preview.attendanceMarksCount()).isEqualTo(7);
        assertThat(preview.linkedGroupCount()).isEqualTo(1);
        assertThat(preview.requiresPassword()).isTrue();
        var beforeProof =
                java.util.List.of(
                        jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target),
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                target),
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_identities WHERE id=?", target),
                        identityFixture
                                .authJdbc()
                                .queryForList(
                                        "SELECT * FROM user_role_grants WHERE user_id=? ORDER BY"
                                                + " id",
                                        target),
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_sessions WHERE sid=?", session),
                        jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class));
        ArchiveRequest denied =
                new ArchiveRequest(
                        UUID.randomUUID(), preview.previewDigest(), "synthetic-wrong-password");
        assertThatThrownBy(() -> archive(target, denied))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        failure ->
                                assertThat(failure.getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(repository.observe(target).status()).isEqualTo("active");
        verify(auth)
                .confirm(target, denied.operationId(), denied.previewDigest(), denied.password());
        assertThat(
                        beforeProof.equals(
                                java.util.List.of(
                                        jdbc.queryForMap(
                                                "SELECT * FROM academic_profiles WHERE id=?",
                                                target),
                                        jdbc.queryForList(
                                                "SELECT * FROM student_group_history WHERE"
                                                        + " user_id=? ORDER BY id",
                                                target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_identities WHERE id=?",
                                                        target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForList(
                                                        "SELECT * FROM user_role_grants WHERE"
                                                                + " user_id=? ORDER BY id",
                                                        target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_sessions WHERE sid=?",
                                                        session),
                                        jdbc.queryForObject(
                                                "SELECT COUNT(*) FROM academic_outbox",
                                                Long.class))))
                .as("A fresh protected operation with wrong password has no account/domain effects")
                .isTrue();

        ArchiveRequest request =
                new ArchiveRequest(
                        UUID.randomUUID(),
                        preview.previewDigest(),
                        AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD);
        var beforeProfile = jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target);
        var beforeHistory =
                jdbc.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id", target);
        long beforeOutbox = jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class);
        doThrow(new IllegalStateException("synthetic receipt write failure"))
                .doCallRealMethod()
                .when(repository)
                .saveReceipt(
                        eq(request.operationId()),
                        eq(owner),
                        eq(target),
                        eq("ARCHIVE"),
                        eq(request.previewDigest()),
                        eq(true));
        assertThatThrownBy(() -> archive(target, request))
                .isInstanceOf(IllegalStateException.class);
        // Only Academic APPLY rolls back. Auth PREPARE and its admission fence are durable.
        assertThat(jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target))
                .isEqualTo(beforeProfile);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                target))
                .isEqualTo(beforeHistory);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class))
                .isEqualTo(beforeOutbox);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_archive_receipt WHERE operation_id=?",
                                Long.class,
                                request.operationId()))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM academic_identity_operations WHERE"
                                        + " operation_id=?",
                                String.class,
                                request.operationId()))
                .isEqualTo("AUTH_PREPARED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT fence_operation_id FROM auth_identities WHERE id=?",
                                        UUID.class,
                                        target))
                .isEqualTo(request.operationId());
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT roles_version FROM auth_identities WHERE id=?",
                                        Long.class,
                                        target))
                .isGreaterThan(studentActor.rolesVersion());
        requireOldSessionDenied();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM student_group_history WHERE user_id=? AND"
                                        + " left_at IS NULL",
                                Long.class,
                                target))
                .isEqualTo(1);
        archive(target, request);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT phase FROM academic_identity_operations WHERE"
                                        + " operation_id=?",
                                String.class,
                                request.operationId()))
                .isEqualTo("SUCCEEDED");
        assertThat(
                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)
                                .equals(retainedHomework))
                .isTrue();
        requireOldSessionDenied();
        long version =
                identityFixture
                        .authJdbc()
                        .queryForObject(
                                "SELECT session_version FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session);
        archive(target, request);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT session_version FROM auth_sessions WHERE sid=?",
                                        Long.class,
                                        session))
                .isEqualTo(version);
        var committedProfile =
                jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target);
        var committedHistory =
                jdbc.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id", target);
        long committedOutbox =
                jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class);
        long committedRolesVersion =
                identityFixture
                        .authJdbc()
                        .queryForObject(
                                "SELECT roles_version FROM auth_identities WHERE id=?",
                                Long.class,
                                target);
        clearInvocations(auth);
        // An exact committed retry reuses accepted material; a fresh ACTIVE target still needs
        // proof
        // (the wrong-password attempt above exercised the actual Auth proof before PREPARE).
        archive(target, request);
        verifyNoInteractions(auth);
        assertThat(jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target))
                .isEqualTo(committedProfile);
        assertThat(
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                target))
                .isEqualTo(committedHistory);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class))
                .isEqualTo(committedOutbox);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT roles_version FROM auth_identities WHERE id=?",
                                        Long.class,
                                        target))
                .isEqualTo(committedRolesVersion);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT session_version FROM auth_sessions WHERE sid=?",
                                        Long.class,
                                        session))
                .isEqualTo(version);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_archive_receipt WHERE operation_id=?",
                                Long.class,
                                request.operationId()))
                .isEqualTo(1);
        var closedHistory =
                jdbc.queryForList(
                        "SELECT id,user_id,group_id,joined_at,left_at,reason FROM"
                                + " student_group_history WHERE user_id=?",
                        target);
        RestoreRequest restore = new RestoreRequest(UUID.randomUUID());
        restore(target, restore);
        restore(target, restore);
        assertThat(
                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)
                                .equals(retainedHomework))
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT COUNT(*) FROM user_role_grants WHERE user_id=? AND"
                                                + " status='active'",
                                        Long.class,
                                        target))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT group_id IS NULL AND NOT is_headman FROM"
                                        + " academic_user_directory WHERE id=?",
                                Boolean.class,
                                target))
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT revoked_at IS NOT NULL AND"
                                            + " revoke_reason='SECURITY_REVOKED' FROM auth_sessions"
                                            + " WHERE sid=?",
                                        Boolean.class,
                                        session))
                .isTrue();
        requireOldSessionDenied();
        assertThat(
                        before.equals(
                                identityFixture
                                        .authJdbc()
                                        .queryForMap(
                                                "SELECT"
                                                    + " login,password_hash,initial_password,telegram_id"
                                                    + " FROM auth_identities WHERE id=?",
                                                target)))
                .as("Archive and restore retain identity and credential material")
                .isTrue();
        assertThat(
                        grants.findByUserId(target).stream()
                                .filter(g -> g.role().equals("STUDENT"))
                                .findFirst()
                                .orElseThrow()
                                .canUpdate())
                .isTrue();
        var restoredState =
                java.util.List.of(
                        jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target),
                        jdbc.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                target),
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_identities WHERE id=?", target),
                        identityFixture
                                .authJdbc()
                                .queryForList(
                                        "SELECT * FROM user_role_grants WHERE user_id=? ORDER BY"
                                                + " id",
                                        target),
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_sessions WHERE sid=?", session),
                        jdbc.queryForObject("SELECT COUNT(*) FROM academic_outbox", Long.class));
        UUID freshRestore = UUID.randomUUID();
        // The public mapper retains the original domain conflict: ACTIVE is not restorable.
        mockMvc.perform(
                        post("/academic/users/{id}/restore", target)
                                .with(identityFixture.signed(adminActor, freshRestore))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operationId\":\"" + freshRestore + "\"}"))
                .andExpect(status().isConflict());
        assertThat(
                        restoredState.equals(
                                java.util.List.of(
                                        jdbc.queryForMap(
                                                "SELECT * FROM academic_profiles WHERE id=?",
                                                target),
                                        jdbc.queryForList(
                                                "SELECT * FROM student_group_history WHERE"
                                                        + " user_id=? ORDER BY id",
                                                target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_identities WHERE id=?",
                                                        target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForList(
                                                        "SELECT * FROM user_role_grants WHERE"
                                                                + " user_id=? ORDER BY id",
                                                        target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_sessions WHERE sid=?",
                                                        session),
                                        jdbc.queryForObject(
                                                "SELECT COUNT(*) FROM academic_outbox",
                                                Long.class))))
                .as("A fresh restore of an ACTIVE account preserves committed state")
                .isTrue();
        long destination =
                groups.createGroup(
                                new CreateGroupRequest(
                                        "УВС-11" + Math.floorMod(System.nanoTime(), 10)))
                        .getId();
        try (var scope = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    target,
                    "STUDENT",
                    new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, destination, null, null));
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT joined_at FROM student_group_history WHERE user_id=? AND"
                                        + " left_at IS NULL",
                                LocalDate.class,
                                target))
                .isEqualTo(today);
        assertThat(
                        jdbc.queryForList(
                                "SELECT id,user_id,group_id,joined_at,left_at,reason FROM"
                                    + " student_group_history WHERE user_id=? AND left_at IS NOT"
                                    + " NULL",
                                target))
                .isEqualTo(closedHistory);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForList(
                                        "SELECT id FROM user_role_grants WHERE user_id=? ORDER BY"
                                                + " id",
                                        Long.class,
                                        target))
                .isEqualTo(grantIds);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT revoked_at IS NOT NULL AND"
                                            + " revoke_reason='SECURITY_REVOKED' FROM auth_sessions"
                                            + " WHERE sid=?",
                                        Boolean.class,
                                        session))
                .isTrue();
        requireOldSessionDenied();
        var freshStudent = identityFixture.select(target, AuthRole.STUDENT);
        assertThat(freshStudent.sessionId()).isNotEqualTo(session);
        assertThat(freshStudent.claims().groupId()).isEqualTo(destination);
        assertThat(liveSessions.admit(freshStudent.claims())).isNotNull();
    }

    @Test
    void stalePreviewAndLegacyArchiveSeamsCannotChangeAuthority() {
        Preview preview = preview(target);
        try (var scope = identityFixture.request(adminActor)) {
            users.patchUser(
                    target,
                    new PatchUserRequest("Changed", null, null, null, null, null, null, null));
        }
        assertThatThrownBy(
                        () ->
                                archive(
                                        target,
                                        new ArchiveRequest(
                                                UUID.randomUUID(),
                                                preview.previewDigest(),
                                                "synthetic")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(auth);
        try (var scope = identityFixture.request(adminActor)) {
            assertThatThrownBy(
                            () ->
                                    users.patchUser(
                                            target,
                                            new PatchUserRequest(
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    AccountStatus.ARCHIVED)))
                    .isInstanceOf(ResponseStatusException.class);
        }
        var controller = new UserController(users, mock(UserAssembler.class), grants, service);
        assertThatThrownBy(() -> controller.archiveUser(target))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> preview(owner)).isInstanceOf(ResponseStatusException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_archive_receipt WHERE target_id=?",
                                Long.class,
                                target))
                .isZero();
    }

    @Test
    void archiveWaitsForHelperWriterThenRevokesItsCommittedAuthorityWithoutFkDeadlock()
            throws Exception {
        long candidate =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Archive",
                                        "Helper",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        610000008002L),
                                adminActor)
                        .userId();
        Preview preview = preview(target);
        ArchiveRequest request =
                new ArchiveRequest(
                        UUID.randomUUID(),
                        preview.previewDigest(),
                        AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD);
        CountDownLatch groupLocked = new CountDownLatch(1), finishHelper = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            var locked = invocation.callRealMethod();
                            groupLocked.countDown();
                            if (!finishHelper.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("helper release timed out");
                            return locked;
                        })
                .when(groupRepository)
                .findByIdForUpdate(group);
        AssignAssistantRequest assign =
                new AssignAssistantRequest(
                        candidate, group, List.of(AssistantPermission.MARK_ATTENDANCE));
        AtomicInteger archivePid = new AtomicInteger();
        doAnswer(
                        invocation -> {
                            archivePid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            return invocation.callRealMethod();
                        })
                .when(repository)
                .verifyAcceptedArchive(eq(target), any());
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> helperWrite = null, archiveWrite = null;
        try {
            helperWrite =
                    workers.submit(
                            () -> {
                                try (var scope = identityFixture.request(headmanActor)) {
                                    tx.executeWithoutResult(
                                            status -> {
                                                assistant.assignAssistant(assign);
                                                // Execute assigned_by's FK KEY SHARE while the
                                                // helper owns the group.
                                                assistantRepository.flush();
                                            });
                                }
                            });
            assertThat(groupLocked.await(10, TimeUnit.SECONDS)).isTrue();
            archiveWrite = workers.submit(() -> archive(target, request));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> {
                                assertThat(archivePid.get()).isPositive();
                                assertThat(
                                                jdbc.queryForObject(
                                                        """
SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock'
  AND query LIKE '%SELECT academic_revision FROM groups WHERE id=%')
""",
                                                        Boolean.class, archivePid.get()))
                                        .isTrue();
                            });
            finishHelper.countDown();
            helperWrite.get(10, TimeUnit.SECONDS);
            archiveWrite.get(10, TimeUnit.SECONDS);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM headman_assistants WHERE group_id=? AND"
                                            + " is_active",
                                    Long.class,
                                    group))
                    .isZero();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT NOT is_active AND revoked_at IS NOT NULL FROM"
                                        + " headman_assistants WHERE group_id=? AND student_id=?",
                                    Boolean.class,
                                    group,
                                    candidate))
                    .isTrue();
            assertThatThrownBy(
                            () -> {
                                try (var scope = identityFixture.request(headmanActor)) {
                                    assistant.assignAssistant(assign);
                                }
                            })
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            denied ->
                                    assertThat(denied.getStatusCode())
                                            .isEqualTo(HttpStatus.UNAUTHORIZED));
            restore(target, new RestoreRequest(UUID.randomUUID()));
            assertThatThrownBy(
                            () -> {
                                try (var scope = identityFixture.request(headmanActor)) {
                                    assistant.assignAssistant(assign);
                                }
                            })
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            denied ->
                                    assertThat(denied.getStatusCode())
                                            .isEqualTo(HttpStatus.UNAUTHORIZED));
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM headman_assistants WHERE group_id=? AND"
                                            + " is_active",
                                    Long.class,
                                    group))
                    .isZero();
        } finally {
            finishHelper.countDown();
            if (helperWrite != null && !helperWrite.isDone()) helperWrite.cancel(true);
            if (archiveWrite != null && !archiveWrite.isDone()) archiveWrite.cancel(true);
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void previewBecomesStaleWhenAnotherTeacherLosesAuthorityAndLocalWarningChanges() {
        // Per-test owned reset isolates committed retained fixtures. A local rollback cannot
        // surround the other teacher's distributed role command.
        long other =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Archive",
                                        "OtherTeacher",
                                        null,
                                        UserRole.TEACHER,
                                        null,
                                        "ARCHIVE-OTHER-01",
                                        null),
                                adminActor)
                        .userId();
        long subject =
                jdbc.queryForObject(
                        "INSERT INTO subjects(name,type,group_id)"
                                + " VALUES(?,'lecture',?) RETURNING id",
                        Long.class,
                        "Archive " + UUID.randomUUID(),
                        group);
        jdbc.update(
                "INSERT INTO subject_lesson_types(subject_id,lesson_type)" + " VALUES(?,'lecture')",
                subject);
        for (long teacher : List.of(target, other))
            jdbc.update(
                    """
INSERT INTO assignments(teacher_id,subject_id,group_id,semester_id,lesson_type,valid_from,valid_until_exclusive)
VALUES(?,?,?,?,'lecture',?,?)
""",
                    teacher,
                    subject,
                    group,
                    semester,
                    today.minusDays(1),
                    today.plusDays(1));
        Preview preview = preview(target);
        assertThat(preview.soleTeacherAssignmentCount()).isZero();
        var unchangedTarget =
                java.util.List.of(
                        jdbc.queryForMap("SELECT * FROM academic_profiles WHERE id=?", target),
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_identities WHERE id=?", target));
        var unchangedGrants =
                identityFixture
                        .authJdbc()
                        .queryForList(
                                "SELECT * FROM user_role_grants WHERE user_id=? ORDER" + " BY id",
                                target);
        try (var scope = identityFixture.request(adminActor)) {
            users.updateRoleGrant(
                    other,
                    "TEACHER",
                    new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        }
        assertThat(repository.observe(target).soleTeacherCount()).isEqualTo(1);
        assertThat(
                        unchangedTarget.equals(
                                java.util.List.of(
                                        jdbc.queryForMap(
                                                "SELECT * FROM academic_profiles WHERE id=?",
                                                target),
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_identities WHERE id=?",
                                                        target))))
                .as("Another teacher's role change retains this account and profile")
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForList(
                                        "SELECT * FROM user_role_grants WHERE user_id=?"
                                                + " ORDER BY id",
                                        target))
                .isEqualTo(unchangedGrants);
        assertThatThrownBy(
                        () ->
                                archive(
                                        target,
                                        new ArchiveRequest(
                                                UUID.randomUUID(),
                                                preview.previewDigest(),
                                                "synthetic")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> {
                            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(e.getReason()).isEqualTo("archive_preview_stale");
                        });
        verifyNoInteractions(auth);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT active_role_grant_id IS NOT NULL FROM"
                                                + " auth_sessions WHERE sid=?",
                                        Boolean.class,
                                        session))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_archive_receipt" + " WHERE target_id=?",
                                Long.class,
                                target))
                .isZero();
    }
}
