package ru.rutcampustrack.academic.homework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.rutcampustrack.academic.AcademicApplication;
import ru.rutcampustrack.academic.exception.AccessDeniedException;
import ru.rutcampustrack.academic.integration.InternalJwtTestConfig;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
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
                "INSERT INTO groups (name, code, is_active) VALUES (?, ?, true) RETURNING id",
                Long.class, "homework-db-it-" + suffix, "hwdb-" + suffix.substring(0, 20));
        semesterId = jdbc.queryForObject(
                "SELECT id FROM semesters WHERE is_active = true ORDER BY id LIMIT 1", Long.class);
        studentId = insertStudent("student-a-" + suffix, "Студент А", groupId);
        otherStudentId = insertStudent("student-b-" + suffix, "Студент Б", groupId);
        subjectId = jdbc.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture'::subject_type, ?) RETURNING id",
                Long.class, "Homework DB IT Subject " + suffix, groupId);
        homeworkId = jdbc.queryForObject(
                "INSERT INTO homeworks "
                        + "(group_id, subject_id, semester_id, lesson_date, lesson_number, title, description, "
                        + "link, published_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 1, ?, ?, NULL, ?, NOW(), NOW()) RETURNING id",
                Long.class, groupId, subjectId, semesterId, LocalDate.of(2026, 3, 2),
                "Concurrent homework " + suffix, "Concurrency fixture", studentId);
    }

    @Test
    void concurrentCompleteCommandsCreateExactlyOneTargetRowAndPreserveOtherStudent() throws Exception {
        insertCompletion(otherStudentId);

        List<Boolean> observed = concurrently(() ->
                service.setCompletion(homeworkId, semesterId, claims(), true));

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
                service.setCompletion(homeworkId, semesterId, claims(), false));

        assertThat(observed).hasSize(CONCURRENT_COMMANDS).containsOnly(false);
        assertThat(completionCount(studentId)).isZero();
        assertThat(completionCount(otherStudentId)).isEqualTo(1);
        assertThat(totalCompletionCount()).isEqualTo(1);
    }

    @Test
    void foreignGroupClaimIsRejectedBeforeRealCompletionWrite() {
        InternalJwtClaims foreignGroupClaims =
                new InternalJwtClaims(studentId, "STUDENT", groupId + 1, false);

        assertThatThrownBy(() -> service.setCompletion(homeworkId, semesterId, foreignGroupClaims, true))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(completionCount(studentId)).isZero();
    }

    private InternalJwtClaims claims() {
        return new InternalJwtClaims(studentId, "STUDENT", groupId, false);
    }

    private long insertStudent(String login, String firstName, long group) {
        return jdbc.queryForObject(
                "INSERT INTO users "
                        + "(login, password_hash, last_name, first_name, role, status, is_headman, group_id, "
                        + "password_changed, created_at, updated_at) "
                        + "VALUES (?, NULL, 'HomeworkDb', ?, 'student'::user_role, 'active'::account_status, "
                        + "false, ?, false, NOW(), NOW()) RETURNING id",
                Long.class, login, firstName, group);
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
}
