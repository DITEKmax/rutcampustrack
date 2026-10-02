package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.grpc.stub.StreamObserver;
import ru.rutcampustrack.academic.AcademicApplication;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.integration.InternalJwtTestConfig;
import ru.rutcampustrack.academic.grpc.StudentHomeworkTestIdentity;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekRequest;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL proof for the student desired-state completion commands.
 *
 * <p>This class owns a fresh, non-reused container and invokes the Spring
 * service proxy from independent executor threads.  The assertions therefore
 * cover transaction boundaries, the native unique-key conflict path and the
 * observable rows that remain after concurrent commands.</p>
 */
@Testcontainers
@SpringBootTest(classes = AcademicApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(InternalJwtTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HomeworkStudentCompletionConcurrencyIT {

    private static final int CONCURRENT_COMMANDS = 8;
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final UUID SESSION_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("academic_homework_it")
            .withUsername("rct_user")
            .withPassword("rct_dev_pass");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.autoconfigure.exclude", () ->
                "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration");
        registry.add("grpc.server.port", () -> "-1");
    }

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HomeworkStudentService service;

    @Autowired
    private AcademicGrpcServiceImpl grpcService;

    @Autowired
    private HomeworkRepository homeworkRepository;

    @Autowired
    private HomeworkBindingArchiveCoordinator archiveCoordinator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private long groupId;
    private long semesterId;
    private long subjectId;
    private long studentId;
    private long otherStudentId;
    private long homeworkId;

    @BeforeEach
    void createIsolatedFixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        groupId = jdbc.queryForObject(
                "INSERT INTO groups (name, is_active) VALUES (?, true) RETURNING id",
                Long.class, "ГР-" + suffix.substring(0, 3).toUpperCase() + "-" + suffix.substring(3, 6));
        semesterId = jdbc.queryForObject(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id LIMIT 1", Long.class);
        studentId = insertStudent("student-a-" + suffix.substring(0, 20), "Студент А", groupId);
        otherStudentId = insertStudent("student-b-" + suffix.substring(0, 20), "Студент Б", groupId);
        subjectId = jdbc.queryForObject(
                "WITH inserted_subject AS ("
                        + "INSERT INTO subjects (name, type, group_id) "
                        + "VALUES (?, 'lecture'::subject_type, ?) "
                        + "RETURNING id, type"
                        + "), inserted_lesson_type AS ("
                        + "INSERT INTO subject_lesson_types (subject_id, lesson_type) "
                        + "SELECT id, type FROM inserted_subject "
                        + "RETURNING subject_id"
                        + ") SELECT subject_id FROM inserted_lesson_type",
                Long.class, "Homework DB IT Subject " + suffix, groupId);
        homeworkId = jdbc.queryForObject(
                "INSERT INTO homeworks "
                        + "(group_id, subject_id, semester_id, lesson_date, lesson_number, title, description, "
                        + "link, published_by, binding_id, actor_id, request_key, payload_hash, "
                        + "publication_state, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 1, ?, ?, NULL, ?, ?, ?, ?, "
                        + "decode(repeat('11', 32), 'hex'), 'ACTIVE', NOW(), NOW()) RETURNING id",
                Long.class, groupId, subjectId, semesterId, LocalDate.of(2026, 3, 2),
                "Concurrent homework " + suffix, "Concurrency fixture", studentId,
                subjectId, studentId, UUID.randomUUID());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationCommittedAfterActiveSelectionRejectsCompletionWithoutChangingStoredRows(boolean completed)
            throws Exception {
        insertCompletion(otherStudentId);
        if (!completed) {
            insertCompletion(studentId);
        }
        var beforeRows = jdbc.queryForList(
                "SELECT student_id, completed_at FROM homework_completions WHERE homework_id = ? ORDER BY student_id",
                homeworkId);
        UUID requestKey = jdbc.queryForObject("SELECT request_key FROM homeworks WHERE id = ?",
                UUID.class, homeworkId);
        CountDownLatch activeSelected = new CountDownLatch(1);
        CountDownLatch cancellationCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> completion = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        var staleActive = homeworkRepository.findById(homeworkId).orElseThrow();
                        assertThat(staleActive.getPublicationState()).isEqualTo(HomeworkPublicationState.ACTIVE);
                        activeSelected.countDown();
                        try {
                            if (!cancellationCommitted.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Cancellation did not commit before completion");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted waiting for cancellation", interrupted);
                        }
                        // The service initially selects the already-cached ACTIVE
                        // entity. Its shared binding lock + refresh must observe
                        // the cancellation before either desired-state write.
                        return service.setCompletion(homeworkId, semesterId, claims(), completed);
                    }));
            assertThat(activeSelected.await(10, TimeUnit.SECONDS)).isTrue();
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    archiveCoordinator.archiveCancelledBinding(subjectId, studentId, requestKey,
                            homeworkId, semesterId, UUID.randomUUID()));
            cancellationCommitted.countDown();

            assertThatThrownBy(() -> completion.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ResourceNotFoundException.class);
            assertThat(jdbc.queryForObject("SELECT publication_state FROM homeworks WHERE id = ?",
                    String.class, homeworkId)).isEqualTo("ARCHIVED");
            assertThat(jdbc.queryForList(
                    "SELECT student_id, completed_at FROM homework_completions WHERE homework_id = ? ORDER BY student_id",
                    homeworkId)).isEqualTo(beforeRows);
        } finally {
            cancellationCommitted.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentCompleteCommandsCreateExactlyOneTargetRowAndPreserveOtherStudent() throws Exception {
        insertCompletion(otherStudentId);

        List<Boolean> observed = concurrently(() ->
                service.setCompletion(homeworkId, semesterId, claims(), true).completed());

        assertThat(observed).hasSize(CONCURRENT_COMMANDS).containsOnly(true);
        assertThat(completionCount(studentId)).isEqualTo(1);
        assertThat(completionCount(otherStudentId)).isEqualTo(1);
        assertThat(totalCompletionCount()).isEqualTo(2);
    }

    @Test
    void concurrentUncompleteCommandsDeleteExactlyTheTargetRowAndPreserveOtherStudent() throws Exception {
        insertCompletion(studentId);
        insertCompletion(otherStudentId);

        List<Boolean> observed = concurrently(() ->
                service.setCompletion(homeworkId, semesterId, claims(), false).completed());

        assertThat(observed).hasSize(CONCURRENT_COMMANDS).containsOnly(false);
        assertThat(completionCount(studentId)).isZero();
        assertThat(completionCount(otherStudentId)).isEqualTo(1);
        assertThat(totalCompletionCount()).isEqualTo(1);
    }

    @Test
    void foreignGroupClaimIsRejectedBeforeRealCompletionWrite() {
        InternalJwtClaims foreignGroupClaims =
                new InternalJwtClaims(studentId, SESSION_ID, 1L, 1L,
                        "STUDENT", "ACTIVE", groupId + 1, false, false);

        assertThatThrownBy(() -> service.setCompletion(homeworkId, semesterId, foreignGroupClaims, true))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(completionCount(studentId)).isZero();
    }

    @Test
    void repeatedCompleteKeepsTimestampUndoClearsItAndRedoGetsFreshTimestamp() throws InterruptedException {
        HomeworkStudentService.CompletionState first =
                service.setCompletion(homeworkId, semesterId, claims(), true);
        HomeworkStudentService.CompletionState repeated =
                service.setCompletion(homeworkId, semesterId, claims(), true);

        assertThat(first.completed()).isTrue();
        assertThat(first.completedAt()).isNotNull();
        assertThat(repeated.completedAt()).isEqualTo(first.completedAt());

        HomeworkStudentService.CompletionState undone =
                service.setCompletion(homeworkId, semesterId, claims(), false);
        assertThat(undone.completed()).isFalse();
        assertThat(undone.completedAt()).isNull();

        Thread.sleep(2L);
        HomeworkStudentService.CompletionState redone =
                service.setCompletion(homeworkId, semesterId, claims(), true);
        assertThat(redone.completedAt()).isAfter(first.completedAt());
    }

    @Test
    void currentCompletionUnionReturnsOldLessonOnceWithStoredTimestamp() {
        LocalDate today = LocalDate.now(MOSCOW);
        jdbc.update("UPDATE homeworks SET lesson_date = ? WHERE id = ?", today.minusDays(1), homeworkId);
        OffsetDateTime completedAt = OffsetDateTime.now(MOSCOW)
                .minusSeconds(1)
                .truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "INSERT INTO homework_completions (homework_id, student_id, completed_at) VALUES (?, ?, ?)",
                homeworkId, studentId, completedAt);

        ZonedDateTime from = today.atStartOfDay(MOSCOW);
        ZonedDateTime to = today.plusDays(1).atStartOfDay(MOSCOW);
        RecordingObserver<HomeworksForWeekResponse> observer = new RecordingObserver<>();
        StudentHomeworkTestIdentity.run(claims(), () -> grpcService.getHomeworksForWeek(
                HomeworksForWeekRequest.newBuilder()
                        .setGroupId(groupId)
                        .setSemesterId(semesterId)
                        .setStudentId(studentId)
                        .setDateFrom(today.toString())
                        .setDateTo(today.toString())
                        .setIncludeCompletedToday(true)
                        .setCompletedTodayFrom(from.toInstant().toString())
                        .setCompletedTodayTo(to.toInstant().toString())
                        .build(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getHomeworksList()).hasSize(1);
        var item = observer.value.getHomeworks(0);
        assertThat(item.getHomeworkId()).isEqualTo(homeworkId);
        assertThat(item.getCompleted()).isTrue();
        assertThat(item.hasCompletedAt()).isTrue();
        assertThat(OffsetDateTime.parse(item.getCompletedAt()).toInstant())
                .isEqualTo(completedAt.toInstant());
    }

    @Test
    void yesterdayCompletionDoesNotLeakIntoTodayUnion() {
        LocalDate today = LocalDate.now(MOSCOW);
        jdbc.update("UPDATE homeworks SET lesson_date = ? WHERE id = ?", today.minusDays(1), homeworkId);
        OffsetDateTime completedAt = OffsetDateTime.now(MOSCOW).minusDays(1);
        jdbc.update(
                "INSERT INTO homework_completions (homework_id, student_id, completed_at) VALUES (?, ?, ?)",
                homeworkId, studentId, completedAt);

        ZonedDateTime from = today.atStartOfDay(MOSCOW);
        ZonedDateTime to = today.plusDays(1).atStartOfDay(MOSCOW);
        RecordingObserver<HomeworksForWeekResponse> observer = new RecordingObserver<>();
        StudentHomeworkTestIdentity.run(claims(), () -> grpcService.getHomeworksForWeek(
                HomeworksForWeekRequest.newBuilder()
                        .setGroupId(groupId)
                        .setSemesterId(semesterId)
                        .setStudentId(studentId)
                        .setDateFrom(today.toString())
                        .setDateTo(today.toString())
                        .setIncludeCompletedToday(true)
                        .setCompletedTodayFrom(from.toInstant().toString())
                        .setCompletedTodayTo(to.toInstant().toString())
                        .build(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getHomeworksList()).isEmpty();
    }

    private InternalJwtClaims claims() {
        return new InternalJwtClaims(studentId, SESSION_ID, 1L, 1L,
                "STUDENT", "ACTIVE", groupId, false, false);
    }

    private long insertStudent(String login, String firstName, long group) {
        long id = jdbc.queryForObject(
                "INSERT INTO users "
                        + "(login, password_hash, last_name, first_name, role, status, is_headman, group_id, "
                        + "password_changed, created_at, updated_at) "
                        + "VALUES (?, NULL, 'HomeworkDb', ?, 'student'::user_role, 'active'::account_status, "
                        + "false, ?, false, NOW(), NOW()) RETURNING id",
                Long.class, login, firstName, group);
        jdbc.update("INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                + "VALUES (?, 'student'::user_role, 'active'::account_status, ?, NOW(), NOW())", id, group);
        return id;
    }

    private void insertCompletion(long student) {
        jdbc.update(
                "INSERT INTO homework_completions (homework_id, student_id) VALUES (?, ?)",
                homeworkId, student);
    }

    private int completionCount(long student) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ? AND student_id = ?",
                Integer.class, homeworkId, student);
    }

    private int totalCompletionCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ?",
                Integer.class, homeworkId);
    }

    private <T> List<T> concurrently(Callable<T> command) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_COMMANDS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_COMMANDS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(CONCURRENT_COMMANDS);
        try {
            for (int index = 0; index < CONCURRENT_COMMANDS; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent command barrier timed out");
                    }
                    return command.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .as("all transaction threads must be ready before the release")
                    .isTrue();
            start.countDown();

            List<T> results = new ArrayList<>(CONCURRENT_COMMANDS);
            for (Future<T> future : futures) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException error) {
                    throw new AssertionError("Concurrent desired-state command failed", error.getCause());
                }
            }
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS))
                    .as("concurrent command executor must stop")
                    .isTrue();
        }
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
