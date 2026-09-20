package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.hateoas.EntityModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.PatchUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserCreatedResponse;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.StudentGroupHistory;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.history.HistoricalMembershipException;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.user.UserService;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * Proof that the managed writer, rather than raw fixtures, creates the
 * coverage marker and initial/transfer history consumed by dated reads.
 */
class HistoricalMembershipIT extends AbstractAcademicIntegrationTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserService userService;

    @Autowired
    private HistoricalMembershipService historicalMembershipService;

    @Autowired
    private UserRepository userRepository;

    @MockitoSpyBean
    private StudentGroupHistoryRepository historyRepository;

    private final List<Long> createdGroupIds = new ArrayList<>();
    private Long createdUserId;
    private Long testSemesterId;
    private List<Long> previousActiveSemesterIds;

    @BeforeEach
    void installCurrentActiveSemester() {
        previousActiveSemesterIds = jdbcTemplate.query(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id",
                (rs, rowNum) -> rs.getLong(1));
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        LocalDate today = LocalDate.now(MOSCOW);
        testSemesterId = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) "
                        + "VALUES (?, ?, ?, true, NOW()) RETURNING id",
                Long.class, "L5B historical " + UUID.randomUUID(),
                today.minusDays(7), today.plusDays(7));
    }

    @AfterEach
    void cleanManagedRows() {
        if (createdUserId != null) {
            jdbcTemplate.update("DELETE FROM student_group_history WHERE user_id = ?", createdUserId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", createdUserId);
        }
        for (Long groupId : createdGroupIds) {
            jdbcTemplate.update("DELETE FROM group_history_coverage WHERE group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM groups WHERE id = ?", groupId);
        }
        if (testSemesterId != null) {
            jdbcTemplate.update("DELETE FROM semesters WHERE id = ?", testSemesterId);
        }
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        for (Long semesterId : previousActiveSemesterIds) {
            jdbcTemplate.update("UPDATE semesters SET is_active = true WHERE id = ?", semesterId);
        }
        createdGroupIds.clear();
        createdUserId = null;
    }

    @Test
    void serviceCreatedEnrollmentAndTransferAreDatedAndComplete() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());

        LocalDate semesterStart = LocalDate.now(MOSCOW).minusDays(7);
        assertThat(historicalMembershipService.readRoster(
                source.getId(), semesterStart, testSemesterId).students())
                .extracting(User::getId)
                .containsExactly(student.getId());

        userService.transferStudent(student.getId(),
                new TransferStudentRequest(destination.getId(), "historical test transfer"));

        LocalDate transferDate = LocalDate.now(MOSCOW);
        assertThat(historicalMembershipService.readRoster(
                source.getId(), transferDate.minusDays(1), testSemesterId).students())
                .extracting(User::getId)
                .containsExactly(student.getId());
        assertThat(historicalMembershipService.readRoster(
                source.getId(), transferDate, testSemesterId).students())
                .isEmpty();
        assertThat(historicalMembershipService.readRoster(
                destination.getId(), transferDate, testSemesterId).students())
                .extracting(User::getId)
                .containsExactly(student.getId());

        List<StudentGroupHistory> history = historyRepository.findByUserIdOrderByJoinedAtDesc(student.getId());
        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(row -> assertThat(row.getJoinedAt()).isNotNull());
        assertThat(history.stream().filter(row -> row.getGroupId().equals(source.getId())).findFirst()
                .orElseThrow().getLeftAt()).isEqualTo(transferDate);
        assertThat(history.stream().filter(row -> row.getGroupId().equals(destination.getId())).findFirst()
                .orElseThrow().getJoinedAt()).isEqualTo(transferDate);
    }

    @Test
    void managedEnrollmentRollsBackUserWhenHistoryInsertFails() {
        Group group = createGroup();
        String marker = "L5B-rollback-" + UUID.randomUUID();
        AtomicBoolean historySaveReached = new AtomicBoolean();
        AtomicBoolean userWasPersistedBeforeHistoryFailure = new AtomicBoolean();
        Answer<Object> historyRepositoryCall = springDataProxyDelegate(historyRepository);

        try {
            doAnswer(invocation -> {
                historySaveReached.set(true);
                StudentGroupHistory history = invocation.getArgument(0);
                if (history.getUserId() != null) {
                    userWasPersistedBeforeHistoryFailure.set(jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class,
                            history.getUserId()) == 1);
                }
                throw new IllegalStateException("injected history persistence failure");
            }).when(historyRepository).save(any(StudentGroupHistory.class));

            assertThatThrownBy(() -> userService.createUser(new CreateUserRequest(
                    "L5B", marker, null, UserRole.STUDENT, group.getId(), null,
                    Math.floorMod(System.nanoTime(), 9_000_000_000L) + 100_000L)))
                    .isInstanceOfSatisfying(IllegalStateException.class, error ->
                            assertThat(error).hasMessage("injected history persistence failure"));
        } finally {
            doAnswer(historyRepositoryCall).when(historyRepository).save(any(StudentGroupHistory.class));
        }

        assertThat(historySaveReached)
                .as("the failure must occur at the managed history save seam")
                .hasValue(true);
        assertThat(userWasPersistedBeforeHistoryFailure)
                .as("the managed user row must exist before history persistence fails")
                .hasValue(true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE first_name = ?", Integer.class, marker))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM student_group_history h "
                        + "JOIN users u ON u.id = h.user_id WHERE u.first_name = ?",
                Integer.class, marker))
                .isZero();
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

        doAnswer(invocation -> {
            Object result = historyRepositoryCall.answer(invocation);
            if (pauseOnce.compareAndSet(false, true)) {
                historySnapshotReady.countDown();
                if (!releaseHistoryRead.await(20, TimeUnit.SECONDS)) {
                    throw new AssertionError("dated read barrier timed out");
                }
            }
            return result;
        }).when(historyRepository)
                .findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(source.getId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<HistoricalMembershipService.RosterSnapshot> readFuture = null;
        Future<User> transferFuture = null;
        try {
            readFuture = executor.submit(() -> historicalMembershipService.readRoster(
                    source.getId(), transferDate, testSemesterId));
            assertThat(historySnapshotReady.await(20, TimeUnit.SECONDS))
                    .as("dated read must establish its database snapshot before transfer")
                    .isTrue();

            transferFuture = executor.submit(() -> userService.transferStudent(
                    student.getId(),
                    new TransferStudentRequest(destination.getId(), "concurrent historical test transfer")));
            assertThat(transferFuture.get(20, TimeUnit.SECONDS).getGroupId())
                    .isEqualTo(destination.getId());

            releaseHistoryRead.countDown();
            HistoricalMembershipService.RosterSnapshot preTransferSnapshot =
                    readFuture.get(20, TimeUnit.SECONDS);
            assertThat(preTransferSnapshot.students())
                    .extracting(User::getId)
                    .containsExactly(student.getId());

            assertThat(historicalMembershipService.readRoster(
                    source.getId(), transferDate, testSemesterId).students())
                    .extracting(User::getId)
                    .isEmpty();
            assertThat(historicalMembershipService.readRoster(
                    destination.getId(), transferDate, testSemesterId).students())
                    .extracting(User::getId)
                    .containsExactly(student.getId());
        } finally {
            releaseHistoryRead.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(20, TimeUnit.SECONDS))
                    .as("historical read/transfer workers must terminate")
                    .isTrue();
            doAnswer(historyRepositoryCall).when(historyRepository)
                    .findByGroupIdOrderByUserIdAscJoinedAtAscIdAsc(source.getId());
        }
    }

    @Test
    void unsupportedDirectMembershipMutationLeavesUserUnchanged() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());

        assertThatThrownBy(() -> userService.patchUser(student.getId(),
                new PatchUserRequest(null, null, null, null, destination.getId(), null, null, null)))
                .isInstanceOf(HistoricalMembershipException.class)
                .satisfies(error -> assertThat(((HistoricalMembershipException) error).code())
                        .isEqualTo(HistoricalMembershipException.Code.UNSUPPORTED_MUTATION));

        assertThat(userRepository.findByIdIncludingArchived(student.getId()).orElseThrow().getGroupId())
                .isEqualTo(source.getId());
        assertThat(historyRepository.findByUserIdOrderByJoinedAtDesc(student.getId())).hasSize(1);
    }

    @Test
    void currentAssignmentWithClosedOnlyHistoryFailsClosed() {
        Group group = createGroup();
        User student = createStudent(group.getId());
        LocalDate today = LocalDate.now(MOSCOW);

        jdbcTemplate.update("UPDATE student_group_history SET left_at = ? WHERE user_id = ?",
                today.minusDays(1), student.getId());

        assertThatThrownBy(() -> historicalMembershipService.readRoster(
                group.getId(), today, testSemesterId))
                .isInstanceOfSatisfying(HistoricalMembershipException.class, error ->
                        assertThat(error.code())
                                .isEqualTo(HistoricalMembershipException.Code.FAILED_PRECONDITION));
    }

    @Test
    void currentAssignmentWithoutTargetHistoryFailsClosed() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());
        LocalDate today = LocalDate.now(MOSCOW);

        // Simulate a partial writer failure: the current assignment points at
        // destination while the only history row still belongs to source.
        jdbcTemplate.update("UPDATE users SET group_id = ? WHERE id = ?",
                destination.getId(), student.getId());

        assertThatThrownBy(() -> historicalMembershipService.readRoster(
                destination.getId(), today, testSemesterId))
                .isInstanceOf(HistoricalMembershipException.class);
    }

    private Group createGroup() {
        int number = (int) Math.floorMod(System.nanoTime(), 10);
        String prefix = createdGroupIds.isEmpty() ? "УИТ" : "УВП";
        Group group = groupService.createGroup(new CreateGroupRequest(
                prefix + "-11" + number));
        createdGroupIds.add(group.getId());
        return group;
    }

    private User createStudent(Long groupId) {
        EntityModel<UserCreatedResponse> created = userService.createUser(new CreateUserRequest(
                "L5B", "Student", null, UserRole.STUDENT, groupId, null,
                Math.abs(System.nanoTime()) + 100_000L));
        createdUserId = created.getContent().getId();
        return userRepository.findByIdIncludingArchived(createdUserId).orElseThrow();
    }

    private static Answer<Object> springDataProxyDelegate(Object repositorySpy) {
        Answer<?> defaultAnswer = mockingDetails(repositorySpy)
                .getMockCreationSettings()
                .getDefaultAnswer();
        assertThat(defaultAnswer)
                .as("@MockitoSpyBean must retain Spring Test's delegate to the original Spring Data proxy")
                .isNotNull();
        return invocation -> defaultAnswer.answer(invocation);
    }
}
