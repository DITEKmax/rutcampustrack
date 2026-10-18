package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.admin.AcademicAdminAccess;
import ru.rutcampustrack.academic.assistant.AssistantService;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HeadmanAssistantRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantReader;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.*;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class UserArchiveLifecycleIT extends AbstractAcademicIntegrationTest {
    private static final String OWNED_SEMESTER_NAME = "user-archive-" + UUID.randomUUID();
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired GroupService groups;
    @Autowired UserArchiveRepository repository;
    @Autowired AcademicAdminAccess adminAccess;
    @Autowired ru.rutcampustrack.academic.admin.AcademicAuthDataCommands authCommands;
    @Autowired UserRoleGrantReader grants;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Autowired GroupRepository groupRepository;
    @Autowired UserRepository userRepository;
    @Autowired UserRoleGrantRepository roleGrantRepository;
    @Autowired HeadmanAssistantRepository assistantRepository;
    private final AuthUserArchiveClient auth = mock(AuthUserArchiveClient.class);
    private final AttendanceUserImpactGrpcClient attendance =
            mock(AttendanceUserImpactGrpcClient.class);
    private final RequestContext context = mock(RequestContext.class);
    private long owner, target, semester, group;
    private UUID session;
    private UserArchiveService service;
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
        owner =
                jdbc.queryForObject(
                        """
INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,password_changed,created_at,updated_at)
VALUES (?, 'synthetic-hash','Archive','Admin','admin','active',FALSE,FALSE,NOW(),NOW()) RETURNING id
""",
                        Long.class,
                        "archive-admin-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update(
                "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) "
                        + "VALUES(?,TRUE,'FULL','{}',1)",
                owner);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at)"
                        + " VALUES(?,'admin','active',NOW(),NOW())",
                owner);
        target =
                users.createUser(
                                new CreateUserRequest(
                                        "Archive",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        Math.abs(System.nanoTime()) + 100000))
                        .getContent()
                        .getId();
        users.updateRoleGrant(
                target,
                "TEACHER",
                new RoleGrantUpdateRequest(
                        RoleGrantStatus.ACTIVE, null, "archive-employee-" + target, null));
        jdbc.update("UPDATE users SET is_headman=TRUE WHERE id=?", target);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at)"
                        + " VALUES(?,'headman','active',?,NOW(),NOW())",
                target,
                group);
        session = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)
VALUES (?,?,(SELECT id FROM user_role_grants WHERE user_id=? AND role='student'),1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')
""",
                session,
                target,
                target,
                UUID.randomUUID());
        when(context.getUserId()).thenReturn(owner);
        when(context.getRole()).thenReturn(UserRole.ADMIN);
        when(attendance.preview(target))
                .thenReturn(
                        UserImpactSnapshot.newBuilder()
                                .setUserId(target)
                                .setAttendanceMarksCount(7)
                                .setObservedAtEpochMs(Instant.now().toEpochMilli())
                                .setSnapshotDigest("b".repeat(64))
                                .build());
        service =
                new UserArchiveService(
                        repository,
                        users,
                        auth,
                        attendance,
                        context,
                        transactionManager,
                        null,
                        eventPublisher);
    }

    // The owned reset precedes each test. Retained profiles/history are not
    // deleted through application tables as teardown (V51 uses RESTRICT).

    @Test
    void protectedArchiveReceiptRollsBackAndRestoreRequiresFreshRoleAssignment() {
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
                jdbc.queryForMap(
                        "SELECT login,password_hash,initial_password,telegram_id FROM users WHERE"
                                + " id=?",
                        target);
        var grantIds =
                jdbc.queryForList(
                        "SELECT id FROM user_role_grants WHERE user_id=? ORDER BY id",
                        Long.class,
                        target);
        Preview preview = service.preview(target);
        assertThat(preview.attendanceMarksCount()).isEqualTo(7);
        assertThat(preview.linkedGroupCount()).isEqualTo(1);
        assertThat(preview.requiresPassword()).isTrue();
        ArchiveRequest request =
                new ArchiveRequest(
                        UUID.randomUUID(), preview.previewDigest(), "synthetic-password");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(auth)
                .confirm(
                        target, request.operationId(), request.previewDigest(), request.password());
        assertThatThrownBy(() -> service.archive(target, request))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        reset(auth);
        UserArchiveRepository failing = spy(archiveRepositoryWithAuthority());
        doThrow(new IllegalStateException("synthetic receipt write failure"))
                .when(failing)
                .saveReceipt(
                        eq(request.operationId()),
                        eq(owner),
                        eq(target),
                        eq("ARCHIVE"),
                        eq(request.previewDigest()),
                        eq(true));
        var rollback =
                new UserArchiveService(
                        failing,
                        users,
                        auth,
                        attendance,
                        context,
                        transactionManager,
                        null,
                        eventPublisher);
        assertThatThrownBy(() -> rollback.archive(target, request))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.observe(target).status()).isEqualTo("active");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT active_role_grant_id IS NOT NULL FROM auth_sessions WHERE"
                                        + " sid=?",
                                Boolean.class,
                                session))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM student_group_history WHERE user_id=? AND"
                                        + " left_at IS NULL",
                                Long.class,
                                target))
                .isEqualTo(1);
        service.archive(target, request);
        assertThat(
                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)
                                .equals(retainedHomework))
                .isTrue();
        long version =
                jdbc.queryForObject(
                        "SELECT session_version FROM auth_sessions WHERE sid=?",
                        Long.class,
                        session);
        service.archive(target, request);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT session_version FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session))
                .isEqualTo(version);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied retry"))
                .when(auth)
                .confirm(
                        target, request.operationId(), request.previewDigest(), request.password());
        assertThatThrownBy(() -> service.archive(target, request))
                .isInstanceOf(ResponseStatusException.class);
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
        service.restore(target, restore);
        service.restore(target, restore);
        assertThat(
                        jdbc.queryForList(retainedHomeworkSql, homework, completion, target)
                                .equals(retainedHomework))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_role_grants WHERE user_id=? AND"
                                        + " status='active'",
                                Long.class,
                                target))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT group_id IS NULL AND NOT is_headman FROM users WHERE id=?",
                                Boolean.class,
                                target))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session))
                .isNull();
        assertThat(
                        jdbc.queryForMap(
                                "SELECT login,password_hash,initial_password,telegram_id FROM users"
                                        + " WHERE id=?",
                                target))
                .isEqualTo(before);
        assertThat(
                        grants.findByUserId(target).stream()
                                .filter(g -> g.role().equals("STUDENT"))
                                .findFirst()
                                .orElseThrow()
                                .canUpdate())
                .isTrue();
        assertThatThrownBy(() -> service.restore(target, new RestoreRequest(UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class);
        long destination =
                groups.createGroup(
                                new CreateGroupRequest(
                                        "УВС-11" + Math.floorMod(System.nanoTime(), 10)))
                        .getId();
        users.updateRoleGrant(
                target,
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, destination, null, null));
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
                        jdbc.queryForList(
                                "SELECT id FROM user_role_grants WHERE user_id=? ORDER BY id",
                                Long.class,
                                target))
                .isEqualTo(grantIds);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session))
                .isNull();
    }

    @Test
    void stalePreviewAndLegacyArchiveSeamsCannotChangeAuthority() {
        Preview preview = service.preview(target);
        jdbc.update("UPDATE users SET last_name='Changed' WHERE id=?", target);
        assertThatThrownBy(
                        () ->
                                service.archive(
                                        target,
                                        new ArchiveRequest(
                                                UUID.randomUUID(),
                                                preview.previewDigest(),
                                                "synthetic")))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(auth);
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
        var controller = new UserController(users, mock(UserAssembler.class), grants, service);
        assertThatThrownBy(() -> controller.archiveUser(target))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(owner))
                .isInstanceOf(ResponseStatusException.class);
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
                users.createUser(
                                new CreateUserRequest(
                                        "Archive",
                                        "Helper",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        Math.abs(System.nanoTime()) + 100000))
                        .getContent()
                        .getId();
        Preview preview = service.preview(target);
        ArchiveRequest request =
                new ArchiveRequest(UUID.randomUUID(), preview.previewDigest(), "synthetic");
        RequestContext headman = mock(RequestContext.class);
        when(headman.getUserId()).thenReturn(target);
        when(headman.getGroupId()).thenReturn(group);
        when(headman.isHeadman()).thenReturn(true);
        CountDownLatch groupLocked = new CountDownLatch(1), finishHelper = new CountDownLatch(1);
        GroupRepository gatedGroups = mock(GroupRepository.class);
        when(gatedGroups.findByIdForUpdate(group))
                .thenAnswer(
                        invocation -> {
                            var locked = groupRepository.findByIdForUpdate(group);
                            groupLocked.countDown();
                            if (!finishHelper.await(10, TimeUnit.SECONDS))
                                throw new IllegalStateException("helper release timed out");
                            return locked;
                        });
        AssistantService gatedAssistant =
                new AssistantService(
                        assistantRepository,
                        userRepository,
                        gatedGroups,
                        roleGrantRepository,
                        headman);
        AssistantService assistant =
                new AssistantService(
                        assistantRepository,
                        userRepository,
                        groupRepository,
                        roleGrantRepository,
                        headman);
        AssignAssistantRequest assign =
                new AssignAssistantRequest(
                        candidate, group, List.of(AssistantPermission.MARK_ATTENDANCE));
        AtomicInteger archivePid = new AtomicInteger();
        UserArchiveRepository observed = spy(archiveRepositoryWithAuthority());
        doAnswer(
                        invocation -> {
                            archivePid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            return invocation.callRealMethod();
                        })
                .when(observed)
                .lock(owner, target, request.operationId());
        UserArchiveService archive =
                new UserArchiveService(
                        observed,
                        users,
                        auth,
                        attendance,
                        context,
                        transactionManager,
                        null,
                        eventPublisher);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Future<?> helperWrite = null, archiveWrite = null;
        try {
            helperWrite =
                    workers.submit(
                            () ->
                                    tx.executeWithoutResult(
                                            status -> {
                                                gatedAssistant.assignAssistant(assign);
                                                assistantRepository
                                                        .flush(); // Executes the assigned_by FK KEY
                                                // SHARE before releasing the
                                                // group.
                                            }));
            assertThat(groupLocked.await(10, TimeUnit.SECONDS)).isTrue();
            archiveWrite = workers.submit(() -> archive.archive(target, request));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> {
                                assertThat(archivePid.get()).isPositive();
                                assertThat(
                                                jdbc.queryForObject(
                                                        """
SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid=? AND wait_event_type='Lock'
  AND query LIKE '%SELECT id FROM groups WHERE id IN%')
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
                            () ->
                                    tx.executeWithoutResult(
                                            status -> assistant.assignAssistant(assign)))
                    .isInstanceOf(AccessDeniedException.class);
            service.restore(target, new RestoreRequest(UUID.randomUUID()));
            assertThatThrownBy(
                            () ->
                                    tx.executeWithoutResult(
                                            status -> assistant.assignAssistant(assign)))
                    .isInstanceOf(AccessDeniedException.class);
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

    private UserArchiveRepository archiveRepositoryWithAuthority() {
        UserArchiveRepository result = new UserArchiveRepository(jdbc);
        // Manual failure/lock probes still execute the real persisted admin authority.
        org.springframework.test.util.ReflectionTestUtils.setField(
                result, "adminAccess", adminAccess);
        org.springframework.test.util.ReflectionTestUtils.setField(
                result, "authCommands", authCommands);
        return result;
    }

    @Test
    void previewBecomesStaleWhenAnotherTeacherLosesAuthorityAndLocalWarningChanges() {
        // Assignment identity/history cannot be deleted. Roll back only this test's synthetic
        // assignments and subject instead of weakening their database retention guards.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            status.setRollbackOnly();
                            long other =
                                    jdbc.queryForObject(
                                            """
INSERT INTO users(login,password_hash,last_name,first_name,role,status,is_headman,password_changed,created_at,updated_at,employee_number)
VALUES (?, 'synthetic-hash','Archive','OtherTeacher','teacher','active',FALSE,FALSE,NOW(),NOW(),'ARCHIVE-OTHER-01') RETURNING id
""",
                                            Long.class,
                                            "archive-other-"
                                                    + UUID.randomUUID().toString().substring(0, 8));
                            jdbc.update(
                                    "INSERT INTO"
                                        + " user_role_grants(user_id,role,status,created_at,updated_at)"
                                        + " VALUES(?,'teacher','active',NOW(),NOW())",
                                    other);
                            long subject =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects(name,type,group_id)"
                                                    + " VALUES(?,'lecture',?) RETURNING id",
                                            Long.class,
                                            "Archive " + UUID.randomUUID(),
                                            group);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
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
                            Preview preview = service.preview(target);
                            assertThat(preview.soleTeacherAssignmentCount()).isZero();
                            var unchangedTarget =
                                    jdbc.queryForMap(
                                            "SELECT to_jsonb(u)::text AS state FROM users u WHERE"
                                                    + " id=?",
                                            target);
                            var unchangedGrants =
                                    jdbc.queryForList(
                                            "SELECT * FROM user_role_grants WHERE user_id=? ORDER"
                                                    + " BY id",
                                            target);
                            jdbc.update(
                                    "UPDATE user_role_grants SET"
                                        + " status='suspended',updated_at=NOW() WHERE user_id=? AND"
                                        + " role='teacher'",
                                    other);
                            assertThat(repository.observe(target).soleTeacherCount()).isEqualTo(1);
                            assertThat(
                                            jdbc.queryForMap(
                                                    "SELECT to_jsonb(u)::text AS state FROM users u"
                                                            + " WHERE id=?",
                                                    target))
                                    .isEqualTo(unchangedTarget);
                            assertThat(
                                            jdbc.queryForList(
                                                    "SELECT * FROM user_role_grants WHERE user_id=?"
                                                            + " ORDER BY id",
                                                    target))
                                    .isEqualTo(unchangedGrants);
                            assertThatThrownBy(
                                            () ->
                                                    service.archive(
                                                            target,
                                                            new ArchiveRequest(
                                                                    UUID.randomUUID(),
                                                                    preview.previewDigest(),
                                                                    "synthetic")))
                                    .isInstanceOfSatisfying(
                                            ResponseStatusException.class,
                                            e -> {
                                                assertThat(e.getStatusCode())
                                                        .isEqualTo(HttpStatus.CONFLICT);
                                                assertThat(e.getReason())
                                                        .isEqualTo("archive_preview_stale");
                                            });
                            verifyNoInteractions(auth);
                            assertThat(repository.observe(target).status()).isEqualTo("active");
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT active_role_grant_id IS NOT NULL FROM"
                                                            + " auth_sessions WHERE sid=?",
                                                    Boolean.class,
                                                    session))
                                    .isTrue();
                            assertThat(
                                            jdbc.queryForObject(
                                                    "SELECT COUNT(*) FROM user_archive_receipt"
                                                            + " WHERE target_id=?",
                                                    Long.class,
                                                    target))
                                    .isZero();
                        });
    }
}
