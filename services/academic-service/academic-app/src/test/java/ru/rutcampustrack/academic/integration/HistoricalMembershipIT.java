package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mockingDetails;

import io.grpc.Status;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityDomainWriter;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityPendingException;
import ru.rutcampustrack.academic.identityoperation.JdbcAcademicIdentityOperationStore;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Proof that the managed writer, rather than raw fixtures, creates the coverage marker and
 * initial/transfer history consumed by dated reads.
 */
class HistoricalMembershipIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private GroupService groupService;

    @Autowired private UserService userService;

    @Autowired private HistoricalMembershipService historicalMembershipService;

    @Autowired private UserRepository userRepository;

    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoSpyBean private StudentGroupHistoryRepository historyRepository;
    @MockitoSpyBean private AcademicIdentityDomainWriter domainWriter;
    @MockitoSpyBean private OutboxStorage outboxStorage;
    @jakarta.persistence.PersistenceContext private EntityManager entityManager;
    @Autowired private JdbcAcademicIdentityOperationStore operations;
    @Autowired private AuthIdentityDataClient identityData;
    @Autowired private AcademicLiveSessionClient liveSessions;
    private SelectedActor admin;

    private final List<Long> createdGroupIds = new ArrayList<>();
    private Long createdUserId;
    private Long testSemesterId;
    private List<Long> previousActiveSemesterIds;

    @BeforeEach
    void installCurrentActiveSemester() {
        previousActiveSemesterIds =
                jdbcTemplate.query(
                        "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                        (rs, rowNum) -> rs.getLong(1));
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        LocalDate today = LocalDate.now(MOSCOW);
        List<Long> writableSemesters =
                jdbcTemplate.query(
                        "SELECT id FROM semesters WHERE date_from <= ? AND date_to >= ? "
                                + "AND NOT is_archived AND archive_transition = 'NONE' "
                                + "AND NOT archive_release_pending "
                                + "ORDER BY (name LIKE 'L5B historical %') DESC, id LIMIT 1",
                        (rs, rowNum) -> rs.getLong(1), today, today);
        testSemesterId =
                writableSemesters.isEmpty()
                        ? jdbcTemplate.queryForObject(
                                "INSERT INTO semesters (name, date_from, date_to, is_active,"
                                        + " created_at) VALUES (?, ?, ?, true, NOW()) RETURNING id",
                                Long.class,
                                "L5B historical " + UUID.randomUUID(),
                                today.minusDays(7),
                                today.plusDays(7))
                        : writableSemesters.get(0);
        jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", testSemesterId);
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
    }

    @AfterEach
    void cleanManagedRows() {
        // The owned two-DB reset precedes each test. Retained history, identities,
        // operation receipts and backfill replay rows are not deleted through application tables.
        // The disposable Testcontainers fixture is retained and reused. Semester
        // deletion now requires the real multi-service saga; cleanup must not
        // bypass its receipts guard merely to remove this empty test semester.
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long semesterId : previousActiveSemesterIds) {
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
        }
        createdGroupIds.clear();
        createdUserId = null;
    }

    @Test
    void terminalRoleGrantClosesHistoryAndReactivationStartsCanonicalInterval() {
        Group group = createGroup();
        User student = createStudent(group.getId());
        LocalDate transitionDate = LocalDate.now(MOSCOW);

        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.EXPELLED, null, null, null));

        List<StudentGroupHistory> afterExpulsion =
                historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId());
        assertThat(afterExpulsion).hasSize(1);
        assertThat(afterExpulsion.get(0).getLeftAt()).isEqualTo(transitionDate);

        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, null, null, null));

        List<StudentGroupHistory> afterReactivation =
                historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId());
        assertThat(afterReactivation).hasSize(2);
        assertThat(afterReactivation.get(0).getLeftAt()).isEqualTo(transitionDate);
        assertThat(afterReactivation.get(1).getJoinedAt()).isEqualTo(transitionDate);
        assertThat(afterReactivation.get(1).getLeftAt()).isNull();

        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.GRADUATED, null, null, null));

        List<StudentGroupHistory> afterGraduation =
                historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId());
        assertThat(afterGraduation).hasSize(2);
        assertThat(afterGraduation.get(1).getLeftAt()).isEqualTo(transitionDate);
    }

    @Test
    void selectionClearRollsBackWithGrantAndLegacySuspensionAlsoClearsSelection() {
        Group group = createGroup();
        User student = createStudent(group.getId());
        long studentGrant = grantId(student.getId(), "student");
        var selected = identityFixture.select(student.getId(), AuthRole.STUDENT);
        UUID session = selected.sessionId();
        long initialSessionVersion = selected.sessionVersion();
        long originalRolesVersion = rolesVersion(student.getId());
        var beforeProfile =
                jdbcTemplate.queryForMap(
                        "SELECT * FROM academic_profiles WHERE id=?", student.getId());
        var beforeHistory =
                jdbcTemplate.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                        student.getId());
        var beforeEligibility =
                jdbcTemplate.queryForList(
                        "SELECT * FROM academic_role_eligibility WHERE user_id=? ORDER BY role",
                        student.getId());
        long beforeOutbox =
                jdbcTemplate.queryForObject("SELECT count(*) FROM academic_outbox", Long.class);
        AtomicBoolean appliedBeforeFailure = new AtomicBoolean();
        doAnswer(
                        call -> {
                            call.callRealMethod();
                            entityManager.flush();
                            assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT directory_status FROM academic_profiles"
                                                            + " WHERE id=?",
                                                    String.class,
                                                    student.getId()))
                                    .isEqualTo("SUSPENDED");
                            assertThat(
                                            jdbcTemplate.queryForObject(
                                                    "SELECT status FROM academic_role_eligibility"
                                                            + " WHERE user_id=? AND role='STUDENT'",
                                                    String.class,
                                                    student.getId()))
                                    .isEqualTo("SUSPENDED");
                            appliedBeforeFailure.set(true);
                            throw new DataAccessResourceFailureException(
                                    "injected failure after role revocation");
                        })
                .when(domainWriter)
                .apply(any(), any(), any(), anyLong());
        UUID operation = UUID.randomUUID();
        var suspend = new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null);
        try {
            assertThatThrownBy(
                            () -> updateRoleGrant(student.getId(), "STUDENT", suspend, operation))
                    .isInstanceOfSatisfying(
                            AcademicIdentityPendingException.class,
                            pending -> assertPending(pending, operation));
        } finally {
            doCallRealMethod().when(domainWriter).apply(any(), any(), any(), anyLong());
        }
        assertThat(appliedBeforeFailure)
                .as("real local suspension DML preceded the injected fault")
                .isTrue();
        assertThat(
                        jdbcTemplate.queryForMap(
                                "SELECT * FROM academic_profiles WHERE id=?", student.getId()))
                .isEqualTo(beforeProfile);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                student.getId()))
                .isEqualTo(beforeHistory);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT * FROM academic_role_eligibility WHERE user_id=? ORDER BY"
                                        + " role",
                                student.getId()))
                .isEqualTo(beforeEligibility);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM academic_outbox", Long.class))
                .isEqualTo(beforeOutbox);
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("active");
        // Only local APPLY rolled back: Auth PREPARE/version/fence are durable.
        assertThat(rolesVersion(student.getId())).isEqualTo(originalRolesVersion + 1);
        assertPrepared(operation, student.getId());
        assertSelection(session, studentGrant, initialSessionVersion);
        requireOldSessionDenied(selected);
        var fencedSnapshot = sessionSnapshot(selected);
        assertThat(fencedSnapshot.succeeded()).isFalse();
        assertThat(fencedSnapshot.failureCode())
                .isEqualTo(SessionStatePort.FailureCode.INVALID_SESSION);
        assertSelection(session, studentGrant, initialSessionVersion);
        requireOldSessionDenied(selected);

        updateRoleGrant(student.getId(), "STUDENT", suspend, operation);
        assertThat(operations.find(operation.toString()).phase().name()).isEqualTo("SUCCEEDED");
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("suspended");
        assertThat(rolesVersion(student.getId())).isEqualTo(originalRolesVersion + 1);
        var suspended = sessionSnapshot(selected);
        assertThat(suspended.succeeded()).isTrue();
        assertThat(suspended.snapshot().activeRole()).isNull();
        assertSelection(session, null, initialSessionVersion + 1);
        requireOldSessionDenied(selected);

        patchUser(
                student.getId(),
                new PatchUserRequest(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ru.rutcampustrack.academic.contract.enums.AccountStatus.SUSPENDED));
        assertThat(sessionSnapshot(selected).succeeded()).isTrue();
        assertSelection(session, null, initialSessionVersion + 1);
        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, null, null, null));
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("active");
        var reactivated = sessionSnapshot(selected);
        assertThat(reactivated.succeeded()).isTrue();
        assertThat(reactivated.snapshot().activeRole()).isNull();
        assertSelection(session, null, initialSessionVersion + 1);
        requireOldSessionDenied(selected);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                                student.getId()))
                .isEqualTo(beforeHistory);
    }

    @Test
    void managedEnrollmentRollsBackUserWhenHistoryInsertFails() {
        Group group = createGroup();
        String marker = "L5B-rollback-" + UUID.randomUUID();
        AtomicBoolean historySaveReached = new AtomicBoolean();
        AtomicBoolean userWasPersistedBeforeHistoryFailure = new AtomicBoolean();
        AtomicLong attemptedUser = new AtomicLong();
        Answer<Object> historyRepositoryCall = springDataProxyDelegate(historyRepository);
        var before = backfillCounts();
        UUID operation = UUID.randomUUID();
        var request =
                new CreateUserRequest(
                        "L5B",
                        marker,
                        null,
                        UserRole.STUDENT,
                        group.getId(),
                        null,
                        Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L);
        try {
            doAnswer(
                            invocation -> {
                                @SuppressWarnings("unchecked")
                                var found =
                                        (java.util.Optional<StudentGroupHistory>)
                                                historyRepositoryCall.answer(invocation);
                                StudentGroupHistory history = found.orElseThrow();
                                attemptedUser.set(history.getUserId());
                                entityManager.flush();
                                historySaveReached.set(true);
                                userWasPersistedBeforeHistoryFailure.set(
                                        jdbcTemplate.queryForObject(
                                                        "SELECT COUNT(*) FROM academic_profiles"
                                                                + " WHERE id = ?",
                                                        Integer.class,
                                                        history.getUserId())
                                                == 1);
                                assertThat(
                                                jdbcTemplate.queryForObject(
                                                        "SELECT count(*) FROM student_group_history"
                                                                + " WHERE id=?",
                                                        Long.class,
                                                        history.getId()))
                                        .isOne();
                                throw new DataAccessResourceFailureException(
                                        "injected history persistence failure");
                            })
                    .when(historyRepository)
                    .findById(anyLong());
            assertThatThrownBy(() -> createUser(request, operation))
                    .isInstanceOfSatisfying(
                            AcademicIdentityPendingException.class,
                            pending -> assertPending(pending, operation));
        } finally {
            doAnswer(historyRepositoryCall).when(historyRepository).findById(anyLong());
        }
        assertThat(historySaveReached)
                .as("the failure must occur after the actual managed history write")
                .isTrue();
        assertThat(userWasPersistedBeforeHistoryFailure)
                .as("the managed profile row must exist before history persistence fails")
                .isTrue();
        assertThat(attemptedUser.get()).isPositive();
        long user = attemptedUser.get();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM academic_profiles WHERE first_name = ?",
                                Integer.class,
                                marker))
                .isZero();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM student_group_history h JOIN"
                                    + " academic_profiles u ON u.id = h.user_id WHERE u.first_name"
                                    + " = ?",
                                Integer.class,
                                marker))
                .isZero();
        assertThat(backfillCounts()).isEqualTo(before);
        assertPrepared(operation, user);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT account_state FROM auth_identities WHERE id=?",
                                        String.class,
                                        user))
                .isEqualTo("PENDING");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        user))
                .isZero();
        admitted(
                UUID.randomUUID(),
                () -> {
                    assertThatThrownBy(
                                    () ->
                                            identityData.initialCredentials(
                                                    List.of(user),
                                                    System.nanoTime()
                                                            + TimeUnit.SECONDS.toNanos(3)))
                            .satisfies(
                                    error ->
                                            assertThat(Status.fromThrowable(error).getCode())
                                                    .isIn(
                                                            Status.Code.PERMISSION_DENIED,
                                                            Status.Code.FAILED_PRECONDITION));
                    return null;
                });
        var recovered = createUser(request, operation);
        assertThat(recovered.getId()).isEqualTo(user);
        assertThat(operations.find(operation.toString()).phase().name()).isEqualTo("SUCCEEDED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM auth_identities WHERE"
                                                + " provision_operation_id=?",
                                        Long.class,
                                        operation))
                .isOne();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=? AND"
                                    + " group_id=? AND joined_at=(SELECT date_from FROM semesters"
                                    + " WHERE id=?) AND left_at IS NULL",
                                Long.class,
                                user,
                                group.getId(),
                                testSemesterId))
                .isOne();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM academic_initial_membership_backfills WHERE"
                                        + " student_id=?",
                                Long.class,
                                user))
                .isOne();
        assertThat(grantStatus(user, "student")).isEqualTo("active");
        var recoveredCounts = backfillCounts();
        assertThat(createUser(request, operation).getId()).isEqualTo(user);
        assertThat(backfillCounts()).isEqualTo(recoveredCounts);
        assertThat(identityFixture.select(user, AuthRole.STUDENT).userId()).isEqualTo(user);
    }

    @Test
    void datedReadUsesOneRepeatableReadSnapshotWhileTransferCommits() throws Exception {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());
        LocalDate transferDate = LocalDate.now(MOSCOW);
        CountDownLatch historySnapshotReady = new CountDownLatch(1);
        CountDownLatch releaseHistoryRead = new CountDownLatch(1);
        AtomicBoolean pauseOnce = new AtomicBoolean();
        Answer<Object> historyRepositoryCall = springDataProxyDelegate(historyRepository);

        doAnswer(
                        invocation -> {
                            Object result = historyRepositoryCall.answer(invocation);
                            if (pauseOnce.compareAndSet(false, true)) {
                                historySnapshotReady.countDown();
                                if (!releaseHistoryRead.await(20, TimeUnit.SECONDS)) {
                                    throw new AssertionError("dated read barrier timed out");
                                }
                            }
                            return result;
                        })
                .when(historyRepository)
                .findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(source.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<HistoricalMembershipService.RosterSnapshot> readFuture = null;
        Future<User> transferFuture = null;
        try {
            readFuture =
                    executor.submit(
                            () ->
                                    historicalMembershipService.readRoster(
                                            source.getId(), transferDate, testSemesterId));
            assertThat(historySnapshotReady.await(20, TimeUnit.SECONDS))
                    .as("dated read must establish its database snapshot before transfer")
                    .isTrue();

            transferFuture =
                    executor.submit(
                            () ->
                                    transferStudent(
                                            student.getId(),
                                            new TransferStudentRequest(
                                                    destination.getId(),
                                                    "concurrent historical test transfer")));
            assertThat(transferFuture.get(20, TimeUnit.SECONDS).getGroupId())
                    .isEqualTo(destination.getId());

            releaseHistoryRead.countDown();
            HistoricalMembershipService.RosterSnapshot preTransferSnapshot =
                    readFuture.get(20, TimeUnit.SECONDS);
            assertThat(preTransferSnapshot.students())
                    .extracting(User::getId)
                    .containsExactly(student.getId());

            assertThat(
                            historicalMembershipService
                                    .readRoster(source.getId(), transferDate, testSemesterId)
                                    .students())
                    .extracting(User::getId)
                    .isEmpty();
            assertThat(
                            historicalMembershipService
                                    .readRoster(destination.getId(), transferDate, testSemesterId)
                                    .students())
                    .extracting(User::getId)
                    .containsExactly(student.getId());
        } finally {
            releaseHistoryRead.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("historical read/transfer workers must terminate")
                    .isTrue();
            doAnswer(historyRepositoryCall)
                    .when(historyRepository)
                    .findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(source.getId());
        }
    }

    @Test
    void unsupportedDirectMembershipMutationLeavesUserUnchanged() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());
        var originalHistory =
                historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId());
        var originalProfile =
                jdbcTemplate.queryForMap(
                        "SELECT * FROM academic_profiles WHERE id=?", student.getId());
        long originalRolesVersion = rolesVersion(student.getId());

        assertThatThrownBy(
                        () ->
                                patchUser(
                                        student.getId(),
                                        new PatchUserRequest(
                                                null,
                                                null,
                                                null,
                                                null,
                                                destination.getId(),
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(ru.rutcampustrack.academic.exception.BadRequestException.class)
                .hasMessage("Изменение группы пользователя выполняется только через перевод");

        assertThat(
                        userRepository
                                .findByIdIncludingArchived(student.getId())
                                .orElseThrow()
                                .getGroupId())
                .isEqualTo(source.getId());
        assertThat(historyRepository.findByUserIdOrderByJoinedAtDesc(student.getId())).hasSize(1);
        assertThat(historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId()))
                .usingRecursiveComparison()
                .isEqualTo(originalHistory);
        assertThat(
                        jdbcTemplate.queryForMap(
                                "SELECT * FROM academic_profiles WHERE id=?", student.getId()))
                .isEqualTo(originalProfile);
        assertThat(rolesVersion(student.getId())).isEqualTo(originalRolesVersion);
    }

    @Test
    void currentAssignmentWithClosedOnlyHistoryFailsClosed() {
        Group group = createGroup();
        User student = createStudent(group.getId());
        LocalDate today = LocalDate.now(MOSCOW);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                jdbcTemplate.update(
                                        "UPDATE student_group_history SET left_at = ? WHERE user_id"
                                                + " = ?",
                                        today.minusDays(1),
                                        student.getId()));

        assertThatThrownBy(
                        () ->
                                historicalMembershipService.readRoster(
                                        group.getId(), today, testSemesterId))
                .isInstanceOfSatisfying(
                        HistoricalMembershipException.class,
                        error ->
                                assertThat(error.code())
                                        .isEqualTo(
                                                HistoricalMembershipException.Code
                                                        .FAILED_PRECONDITION));
    }

    @Test
    void currentAssignmentWithoutTargetHistoryFailsClosed() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());
        LocalDate today = LocalDate.now(MOSCOW);

        // Simulate a partial writer failure: the current assignment points at
        // destination while the only history row still belongs to source.
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                jdbcTemplate.update(
                                        "UPDATE academic_role_eligibility SET group_id=? WHERE"
                                                + " user_id=? AND role='STUDENT'",
                                        destination.getId(),
                                        student.getId()));

        assertThatThrownBy(
                        () ->
                                historicalMembershipService.readRoster(
                                        destination.getId(), today, testSemesterId))
                .isInstanceOf(HistoricalMembershipException.class);
        assertThatThrownBy(
                        () ->
                                historicalMembershipService.readMemberUserIds(
                                        destination.getId(), today))
                .isInstanceOf(HistoricalMembershipException.class);
    }

    @Autowired private com.fasterxml.jackson.databind.ObjectMapper backfillJson;

    @Autowired
    private ru.rutcampustrack.academic.event
                    .InitialMembershipAttendanceBackfillAcknowledgementConsumer
            backfillAcks;

    @Autowired
    private ru.rutcampustrack.academic.semester.AcademicSemesterArchiveBarrierTransaction
            backfillBarrier;

    @Test
    void initialEnrollmentAndEquivalentRoleAddAtomicallyAdmitPrivateBackfill() throws Exception {
        Group group = createGroup();
        User student = createStudent(group.getId());
        var first = assertPrivateBackfill(student.getId(), group.getId());
        assertThat(first.get("state")).isEqualTo("PENDING");
        // A non-initial status change must not create another enrollment work identity.
        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM academic_initial_membership_backfills WHERE"
                                        + " student_id = ?",
                                Long.class,
                                student.getId()))
                .isEqualTo(1);
        var created =
                createUser(
                        new CreateUserRequest(
                                "L5B",
                                "Teacher",
                                null,
                                UserRole.TEACHER,
                                null,
                                "B13-" + UUID.randomUUID().toString().substring(0, 16),
                                null));
        Long teacherId = created.getId();
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT count(*) FROM academic_initial_membership_backfills WHERE"
                                        + " student_id = ?",
                                Long.class,
                                teacherId))
                .isZero();
        updateRoleGrant(
                teacherId,
                "STUDENT",
                new RoleGrantUpdateRequest(
                        RoleGrantStatus.ACTIVE,
                        group.getId(),
                        null,
                        Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L));
        assertPrivateBackfill(teacherId, group.getId());
    }

    @Test
    void refusedOutboxAndPostFenceEnrollmentRollbackUserHistoryWorkAndOutbox() {
        Group group = createGroup();
        var before = backfillCounts();
        String marker = "B13-refused-" + UUID.randomUUID();
        UUID refusedOperation = UUID.randomUUID();
        var request =
                new CreateUserRequest(
                        "L5B",
                        marker,
                        null,
                        UserRole.STUDENT,
                        group.getId(),
                        null,
                        Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L);
        AtomicBoolean refused = new AtomicBoolean();
        Answer<Object> outboxCall = springDataProxyDelegate(outboxStorage);
        doAnswer(
                        call -> {
                            try {
                                return outboxCall.answer(call);
                            } catch (RuntimeException failure) {
                                for (Throwable cause = failure;
                                        cause != null;
                                        cause = cause.getCause())
                                    if (cause instanceof java.sql.SQLException sql
                                            && "23514".equals(sql.getSQLState())) refused.set(true);
                                throw failure;
                            }
                        })
                .when(outboxStorage)
                .save(eq("student.initial-membership.recorded"), anyString());
        // NOT VALID preserves canonical events; PostgreSQL still checks every new write.
        identityFixture
                .fixtureOwnerAcademicJdbc()
                .execute(
                        "ALTER TABLE academic_outbox ADD CONSTRAINT b13_refuse_initial CHECK"
                            + " (event_type <> 'student.initial-membership.recorded') NOT VALID");
        try {
            assertThatThrownBy(() -> createUser(request, refusedOperation))
                    .isInstanceOf(RuntimeException.class);
            assertThat(refused)
                    .as("owned outbox constraint must refuse initial enrollment")
                    .isTrue();
        } finally {
            identityFixture
                    .fixtureOwnerAcademicJdbc()
                    .execute("ALTER TABLE academic_outbox DROP CONSTRAINT b13_refuse_initial");
            doAnswer(outboxCall)
                    .when(outboxStorage)
                    .save(eq("student.initial-membership.recorded"), anyString());
        }
        assertThat(backfillCounts())
                .as("refused outbox rolls back every Academic enrollment store")
                .isEqualTo(before);
        long refusedUser =
                operations.find(refusedOperation.toString()).prepared().getTargets(0).getUserId();
        assertPrepared(refusedOperation, refusedUser);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT account_state FROM auth_identities WHERE id=?",
                                        String.class,
                                        refusedUser))
                .isEqualTo("PENDING");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        refusedUser))
                .isZero();
        assertThat(createUser(request, refusedOperation).getId()).isEqualTo(refusedUser);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        refusedUser))
                .isOne();
        assertThat(operations.find(refusedOperation.toString()).phase().name())
                .isEqualTo("SUCCEEDED");

        var beforeFence = backfillCounts();
        UUID operation = UUID.randomUUID();
        long version =
                jdbcTemplate.queryForObject(
                        "SELECT state_version FROM semesters WHERE id = ?",
                        Long.class,
                        testSemesterId);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                backfillBarrier.install(
                                        operation,
                                        testSemesterId,
                                        version,
                                        ru.rutcampustrack.academic.contract.enums
                                                .SemesterArchiveAction.ARCHIVE));
        UUID fencedEnrollment = UUID.randomUUID();
        var fencedRequest =
                new CreateUserRequest(
                        "L5B",
                        marker,
                        null,
                        UserRole.STUDENT,
                        group.getId(),
                        null,
                        Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L);
        long fencedUser;
        try {
            assertThatThrownBy(() -> createUser(fencedRequest, fencedEnrollment))
                    .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
            assertThat(backfillCounts())
                    .as("post-fence admission rolls back every Academic enrollment store")
                    .isEqualTo(beforeFence);
            fencedUser =
                    operations
                            .find(fencedEnrollment.toString())
                            .prepared()
                            .getTargets(0)
                            .getUserId();
            assertPrepared(fencedEnrollment, fencedUser);
            assertThat(
                            identityFixture
                                    .authJdbc()
                                    .queryForObject(
                                            "SELECT account_state FROM auth_identities WHERE id=?",
                                            String.class,
                                            fencedUser))
                    .isEqualTo("PENDING");
            assertThat(
                            identityFixture
                                    .authJdbc()
                                    .queryForObject(
                                            "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                            Long.class,
                                            fencedUser))
                    .isZero();
        } finally {
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                            tx ->
                                    jdbcTemplate.update(
                                            "DELETE FROM academic_semester_archive_barriers WHERE"
                                                    + " semester_id = ?",
                                            testSemesterId));
        }
        assertThat(createUser(fencedRequest, fencedEnrollment).getId()).isEqualTo(fencedUser);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT count(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        fencedUser))
                .isOne();
        assertThat(operations.find(fencedEnrollment.toString()).phase().name())
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void onlyExactBackfillAcknowledgementDrainsArchiveAndRetainsReplayIdentity() throws Exception {
        Group group = createGroup();
        User student = createStudent(group.getId());
        var work = assertPrivateBackfill(student.getId(), group.getId());
        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("source_event_id", work.get("event_id").toString());
        payload.put("history_id", ((Number) work.get("history_id")).longValue());
        payload.put("semester_id", testSemesterId);
        payload.put(
                "payload_hash",
                java.util.HexFormat.of().formatHex((byte[]) work.get("payload_hash")));
        payload.put("result", "APPLIED");
        var ack =
                new java.util.LinkedHashMap<String, Object>(
                        java.util.Map.of(
                                "event_id",
                                UUID.randomUUID().toString(),
                                "occurred_at",
                                java.time.Instant.now().toString(),
                                "trace_id",
                                "owned-backfill-ack",
                                "event_type",
                                "student.initial-membership.attendance-backfilled",
                                "source",
                                "attendance-service",
                                "event_version",
                                1,
                                "payload",
                                payload));
        UUID operation = UUID.randomUUID();
        long version =
                jdbcTemplate.queryForObject(
                        "SELECT state_version FROM semesters WHERE id = ?",
                        Long.class,
                        testSemesterId);
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        tx ->
                                backfillBarrier.install(
                                        operation,
                                        testSemesterId,
                                        version,
                                        ru.rutcampustrack.academic.contract.enums
                                                .SemesterArchiveAction.ARCHIVE));
        jdbcTemplate.update(
                "UPDATE semesters SET is_active = false, archive_transition = 'ARCHIVING' WHERE id"
                        + " = ?",
                testSemesterId);
        try {
            assertThat(backfillBarrier.sealArchive(operation, testSemesterId, version)).isNotNull();
            for (String field : List.of("history_id", "semester_id", "payload_hash")) {
                var invalid = new java.util.LinkedHashMap<>(payload);
                invalid.put(
                        field,
                        field.equals("payload_hash")
                                ? "0".repeat(64)
                                : ((Number) payload.get(field)).longValue() + 1);
                var changed = new java.util.LinkedHashMap<>(ack);
                changed.put("payload", invalid);
                assertThatThrownBy(() -> backfillAcks.onEvent(changed))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            var spoof = new java.util.LinkedHashMap<>(ack);
            spoof.put("source", "schedule-service");
            assertThatThrownBy(() -> backfillAcks.onEvent(spoof))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT state FROM academic_initial_membership_backfills WHERE"
                                            + " event_id = ?",
                                    String.class,
                                    work.get("event_id")))
                    .isEqualTo("PENDING");
            backfillAcks.onEvent(ack);
            assertThat(backfillBarrier.sealArchive(operation, testSemesterId, version)).isNull();
            var applied =
                    jdbcTemplate.queryForMap(
                            "SELECT * FROM academic_initial_membership_backfills WHERE event_id ="
                                    + " ?",
                            work.get("event_id"));
            assertThat(applied.get("state")).isEqualTo("APPLIED");
            backfillAcks.onEvent(ack);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT applied_at = ? FROM"
                                        + " academic_initial_membership_backfills WHERE event_id ="
                                        + " ?",
                                    Boolean.class,
                                    applied.get("applied_at"),
                                    work.get("event_id")))
                    .isTrue();
            payload.put("history_id", ((Number) payload.get("history_id")).longValue() + 1);
            assertThatThrownBy(() -> backfillAcks.onEvent(ack))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(
                            jdbcTemplate.queryForObject(
                                    "SELECT count(*) FROM pg_constraint WHERE conrelid ="
                                        + " 'academic_initial_membership_backfills'::regclass AND"
                                        + " contype = 'f'",
                                    Long.class))
                    .isZero();
        } finally {
            jdbcTemplate.update(
                    "UPDATE semesters SET archive_transition = 'NONE', is_active = true WHERE id ="
                            + " ?",
                    testSemesterId);
            jdbcTemplate.update(
                    "DELETE FROM academic_semester_archive_barriers WHERE semester_id = ?",
                    testSemesterId);
        }
    }

    @Test
    void v61IsAdditiveAndRestoresPendingAndAppliedReplayIdentities() {
        String schema = "backfill_" + UUID.randomUUID().toString().replace("-", "");
        String restoredSchema = schema + "_restore";
        identityFixture.fixtureOwnerAcademicJdbc().execute("CREATE SCHEMA " + schema);
        identityFixture.fixtureOwnerAcademicJdbc().execute("CREATE SCHEMA " + restoredSchema);
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        var isolated = new JdbcTemplate(dataSource);
        var prior =
                org.flywaydb.core.Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .locations("classpath:db/migration")
                        .target("60")
                        .load();
        prior.migrate();
        isolated.update(
                "INSERT INTO users (login,last_name,first_name) VALUES"
                        + " ('b13-additive','Synthetic','Enrollment')");
        String before =
                isolated.queryForObject(
                        "SELECT md5(string_agg(to_jsonb(u)::text, '' ORDER BY id)) FROM users u",
                        String.class);
        var history =
                isolated.queryForList(
                        "SELECT installed_rank, version, checksum FROM flyway_schema_history ORDER"
                                + " BY installed_rank");
        var current =
                org.flywaydb.core.Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .locations("classpath:db/migration")
                        .target("61")
                        .load();
        assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
        current.validate();
        assertThat(
                        before.equals(
                                isolated.queryForObject(
                                        "SELECT md5(string_agg(to_jsonb(u)::text, '' ORDER BY id))"
                                                + " FROM users u",
                                        String.class)))
                .as("V61 preserves existing business rows exactly")
                .isTrue();
        assertThat(
                        history.equals(
                                isolated.queryForList(
                                        "SELECT installed_rank, version, checksum FROM"
                                            + " flyway_schema_history ORDER BY installed_rank LIMIT"
                                            + " ?",
                                        history.size())))
                .as("all prior migration identities are unchanged")
                .isTrue();
        UUID pendingId = UUID.randomUUID(), appliedId = UUID.randomUUID();
        for (UUID id : List.of(pendingId, appliedId))
            isolated.update(
                    """
INSERT INTO academic_initial_membership_backfills
  (event_id,history_id,student_id,group_id,semester_id,joined_on,semester_ends_on,payload_hash,state,applied_at)
VALUES (?,?,?,?,?,'2026-09-01','2027-01-31',decode(repeat('ab',32),'hex'),?,?)
""",
                    id,
                    id.equals(pendingId) ? 1001L : 1002L,
                    9001L,
                    9002L,
                    9003L,
                    id.equals(pendingId) ? "PENDING" : "APPLIED",
                    id.equals(pendingId) ? null : java.sql.Timestamp.from(java.time.Instant.now()));
        // Development restore copies exact durable SQL rows into a separately owned V61 schema.
        // It is not a claim about the production backup tool or a distributed transaction.
        var restoredSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + restoredSchema,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        org.flywaydb.core.Flyway.configure()
                .dataSource(restoredSource)
                .schemas(restoredSchema)
                .locations("classpath:db/migration")
                .target("61")
                .load()
                .migrate();
        isolated.execute(
                "INSERT INTO "
                        + restoredSchema
                        + ".academic_initial_membership_backfills SELECT * FROM"
                        + " academic_initial_membership_backfills");
        assertThat(
                        isolated.queryForObject(
                                "SELECT NOT EXISTS((SELECT * FROM"
                                    + " academic_initial_membership_backfills EXCEPT SELECT * FROM "
                                        + restoredSchema
                                        + ".academic_initial_membership_backfills) UNION ALL"
                                        + " (SELECT * FROM "
                                        + restoredSchema
                                        + ".academic_initial_membership_backfills EXCEPT SELECT *"
                                        + " FROM academic_initial_membership_backfills))",
                                Boolean.class))
                .as("pending/applied hashes, tuples and timestamps survive owned SQL restore")
                .isTrue();
        assertThat(current.migrate().migrationsExecuted).isZero();
        // Disposable schemas remain in this fresh class-owned container until teardown.
    }

    private java.util.Map<String, Object> assertPrivateBackfill(long studentId, long groupId)
            throws Exception {
        var work =
                jdbcTemplate.queryForMap(
                        "SELECT * FROM academic_initial_membership_backfills WHERE student_id = ?",
                        studentId);
        var history =
                jdbcTemplate.queryForMap(
                        "SELECT id, joined_at FROM student_group_history WHERE user_id = ?",
                        studentId);
        assertThat(work.get("history_id")).isEqualTo(history.get("id"));
        assertThat(((Number) work.get("group_id")).longValue()).isEqualTo(groupId);
        assertThat(((Number) work.get("semester_id")).longValue()).isEqualTo(testSemesterId);
        assertThat(work.get("joined_on")).isEqualTo(history.get("joined_at"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT semester_ends_on = s.date_to FROM"
                                    + " academic_initial_membership_backfills w JOIN semesters s ON"
                                    + " s.id = w.semester_id WHERE w.event_id = ?",
                                Boolean.class,
                                work.get("event_id")))
                .isTrue();
        String envelopeJson =
                jdbcTemplate.queryForObject(
                        "SELECT payload FROM academic_outbox WHERE event_type ="
                                + " 'student.initial-membership.recorded' AND"
                                + " payload::jsonb->>'event_id' = ?",
                        String.class,
                        work.get("event_id").toString());
        assertThat(
                        ru.rutcampustrack.academic.events.EventSchemaValidator.validate(
                                "student.initial-membership.recorded.json", envelopeJson))
                .isEmpty();
        var envelope = backfillJson.readTree(envelopeJson);
        assertThat(envelope.path("source").asText()).isEqualTo("academic-service");
        assertThat(envelope.path("event_version").asInt()).isEqualTo(1);
        var tuple =
                backfillJson.convertValue(
                        envelope.path("payload"),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                java.util.TreeMap<String, Object>>() {});
        assertThat(tuple.keySet())
                .containsExactlyInAnyOrder(
                        "history_id",
                        "student_id",
                        "group_id",
                        "semester_id",
                        "joined_on",
                        "semester_ends_on");
        assertThat(
                        java.security.MessageDigest.isEqual(
                                (byte[]) work.get("payload_hash"),
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                new com.fasterxml.jackson.databind.ObjectMapper()
                                                        .writeValueAsBytes(tuple))))
                .as("private outbox and durable work bind the same canonical identity")
                .isTrue();
        return work;
    }

    private java.util.Map<String, Long> backfillCounts() {
        return java.util.Map.of(
                "profiles",
                jdbcTemplate.queryForObject("SELECT count(*) FROM academic_profiles", Long.class),
                "eligibility",
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM academic_role_eligibility", Long.class),
                "history",
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM student_group_history", Long.class),
                "work",
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM academic_initial_membership_backfills", Long.class),
                "outbox",
                jdbcTemplate.queryForObject("SELECT count(*) FROM academic_outbox", Long.class));
    }

    private Group createGroup() {
        int number = (int) Math.floorMod(System.nanoTime(), 10);
        String prefix = createdGroupIds.isEmpty() ? "УИТ" : "УВП";
        Group group =
                admitted(
                        UUID.randomUUID(),
                        () ->
                                groupService.createGroup(
                                        new CreateGroupRequest(prefix + "-11" + number)));
        createdGroupIds.add(group.getId());
        return group;
    }

    private User createStudent(Long groupId) {
        createdUserId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "L5B",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        groupId,
                                        null,
                                        Math.floorMod(System.nanoTime(), 9_000_000_000L)
                                                + 100_000L),
                                admin)
                        .userId();
        return userRepository.findByIdIncludingArchived(createdUserId).orElseThrow();
    }

    private UserCreatedResponse createUser(CreateUserRequest request) {
        return createUser(request, UUID.randomUUID());
    }

    private UserCreatedResponse createUser(CreateUserRequest request, UUID operation) {
        return admitted(operation, () -> userService.createUser(request).getContent());
    }

    private User updateRoleGrant(Long user, String role, RoleGrantUpdateRequest request) {
        return updateRoleGrant(user, role, request, UUID.randomUUID());
    }

    private User updateRoleGrant(
            Long user, String role, RoleGrantUpdateRequest request, UUID operation) {
        return admitted(operation, () -> userService.updateRoleGrant(user, role, request));
    }

    private User patchUser(Long user, PatchUserRequest request) {
        return admitted(UUID.randomUUID(), () -> userService.patchUser(user, request));
    }

    private User transferStudent(Long user, TransferStudentRequest request) {
        return admitted(UUID.randomUUID(), () -> userService.transferStudent(user, request));
    }

    private <T> T admitted(UUID operation, Supplier<T> action) {
        try (var ignored = identityFixture.request(admin, operation)) {
            return action.get();
        }
    }

    private long rolesVersion(Long userId) {
        return identityFixture
                .authJdbc()
                .queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id = ?",
                        Long.class,
                        userId);
    }

    private long grantId(Long userId, String role) {
        return identityFixture
                .authJdbc()
                .queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id = ? AND role = ?",
                        Long.class,
                        userId,
                        role);
    }

    private void assertSelection(UUID sessionId, Long grantId, long version) {
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT active_role_grant_id FROM auth_sessions WHERE sid ="
                                                + " ?",
                                        Long.class,
                                        sessionId))
                .isEqualTo(grantId);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT session_version FROM auth_sessions WHERE sid = ?",
                                        Long.class,
                                        sessionId))
                .isEqualTo(version);
    }

    private String grantStatus(Long userId, String role) {
        return identityFixture
                .authJdbc()
                .queryForObject(
                        "SELECT status FROM user_role_grants WHERE user_id = ? AND role = ?",
                        String.class,
                        userId,
                        role);
    }

    private void assertPrepared(UUID operation, long user) {
        assertThat(operations.find(operation.toString()).phase().name()).isEqualTo("AUTH_PREPARED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT phase FROM auth_identity_operations WHERE"
                                                + " operation_id=?",
                                        String.class,
                                        operation))
                .isEqualTo("PREPARED_FENCED");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT fence_operation_id FROM auth_identities WHERE id=?",
                                        UUID.class,
                                        user))
                .isEqualTo(operation);
    }

    private void assertPending(AcademicIdentityPendingException pending, UUID operation) {
        assertThat(pending.operation().operationId()).isEqualTo(operation);
        assertThat(pending.operation().status())
                .isEqualTo(IdentityOperationHttpModels.Status.PENDING);
    }

    private void requireOldSessionDenied(SelectedActor actor) {
        assertThatThrownBy(() -> liveSessions.admit(actor.claims()))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    private SessionStatePort.SnapshotResult sessionSnapshot(SelectedActor actor) {
        // Actual Auth session read owns lazy unselectable-grant reconciliation.
        // Its transaction manager and JDBC both belong to the fixture's Auth database.
        var authJdbc = identityFixture.authJdbc();
        var authority =
                new JdbcSessionAuthority(
                        authJdbc,
                        identityFixture.authTransactions().getTransactionManager(),
                        new PasswordChangedOutbox(authJdbc, backfillJson));
        return authority.snapshot(
                new SessionStatePort.SnapshotCommand(
                        actor.userId(), actor.sessionId(), Instant.now()));
    }

    private static Answer<Object> springDataProxyDelegate(Object repositorySpy) {
        Answer<?> defaultAnswer =
                mockingDetails(repositorySpy).getMockCreationSettings().getDefaultAnswer();
        assertThat(defaultAnswer)
                .as(
                        "@MockitoSpyBean must retain Spring Test's delegate to the original Spring"
                                + " Data proxy")
                .isNotNull();
        return invocation -> defaultAnswer.answer(invocation);
    }
}
